package dev.flexmodel.session;

import com.mongodb.client.MongoDatabase;
import dev.flexmodel.*;
import dev.flexmodel.cache.Cache;
import dev.flexmodel.cache.CachingModelRegistry;
import dev.flexmodel.cache.ConcurrentHashMapCache;
import dev.flexmodel.cache.InMemoryModelRegistry;
import dev.flexmodel.event.EventPublisher;
import dev.flexmodel.event.impl.SimpleEventPublisher;
import dev.flexmodel.model.EntityDefinition;
import dev.flexmodel.model.EnumDefinition;
import dev.flexmodel.model.IndexDefinition;
import dev.flexmodel.model.SchemaObject;
import dev.flexmodel.model.field.TypedField;
import dev.flexmodel.mongodb.MongoContext;
import dev.flexmodel.mongodb.MongoSchemaProvider;
import dev.flexmodel.mongodb.MongoSession;
import dev.flexmodel.service.DataService;
import dev.flexmodel.service.EventAwareDataService;
import dev.flexmodel.service.WriteInterceptor;
import dev.flexmodel.sql.*;
import dev.flexmodel.sql.dialect.SqlDialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author cjbi
 */
public class SessionFactory {

  private final ModelRegistry modelRegistry;
  private final SchemaProvider defaultSchemaProvider;
  private final Map<String, SchemaProvider> schemaProviders = new HashMap<>();
  private final Cache cache;
  private final Logger log = LoggerFactory.getLogger(SessionFactory.class);
  private final MemoryScriptManager memoryScriptManager;
  private final boolean failsafe;
  private final EventPublisher eventPublisher;
  private final List<WriteInterceptor> writeInterceptors;

  SessionFactory(SchemaProvider defaultSchemaProvider, List<SchemaProvider> schemaProviders, Cache cache, boolean failsafe, EventPublisher eventPublisher, List<WriteInterceptor> writeInterceptors) {
    this.cache = cache;
    this.memoryScriptManager = new MemoryScriptManager();
    this.eventPublisher = eventPublisher != null ? eventPublisher : new SimpleEventPublisher();
    this.defaultSchemaProvider = defaultSchemaProvider;
    registerSchemaProvider(defaultSchemaProvider);
    schemaProviders.forEach(this::registerSchemaProvider);
    this.modelRegistry = initializeModelRegistry(defaultSchemaProvider);
    this.failsafe = failsafe;
    this.writeInterceptors = List.copyOf(writeInterceptors);
//    processBuildItem();
  }

  private ModelRegistry initializeModelRegistry(SchemaProvider schemaProvider) {
    if (schemaProvider instanceof JdbcSchemaProvider jdbcSchemaProvider) {
      return new CachingModelRegistry(new JdbcModelRegistry(jdbcSchemaProvider.dataSource()), cache);
    } else if (schemaProvider instanceof MongoSchemaProvider) {
      return new InMemoryModelRegistry();
    } else {
      throw new IllegalArgumentException("Unsupported SchemaProvider");
    }
  }

  /**
   * 处理构建步骤的项目
   */
//  void processBuildItem() {
//    // 将BuildItem脚本加载到内存中（通过 SPI ServiceLoader）
//    memoryScriptManager.loadScriptsFromBuildItems();
//
//    // 将内存中的脚本应用到缓存和数据库
//    memoryScriptManager.getSchemaNames().forEach(this::applyBuildItemSchemas);
//  }

  /**
   * 将指定 schema 的 BuildItem 配置应用到缓存和数据库中。
   * 从内存脚本管理器读取 schema 定义和数据，然后：
   * <ul>
   *   <li>将模型定义写入缓存</li>
   *   <li>在数据库中创建实体和枚举表结构</li>
   *   <li>导入初始数据</li>
   * </ul>
   *
   * @param schemaName 要应用的 schema 名称
   * @param preservedRuntimeModels 需要合并保留运行时实体定义的模型名称
   */
  private void applyBuildItemSchemas(String schemaName, Set<String> preservedRuntimeModels) {
    MemoryScriptManager.SchemaScriptConfig config = memoryScriptManager.getScriptConfig(schemaName);
    if (config == null) {
      return;
    }
    // 始终将模型写入缓存（即使没有数据源，查询时仍能从缓存命中模型定义）
    config.getSchema().forEach(model -> cache.put(schemaName + ":" + model.getName(), model));

    // 如果没有为此 schema 注册数据源，跳过 DDL 和数据导入
    if (!isSchemaExists(schemaName)) {
      log.warn("Schema '{}' has no registered datasource, skipping table/data import", schemaName);
      return;
    }

    try (Session session = createFailsafeSession(schemaName, SessionOptions.schemaImport())) {
      // 先快照已注册的旧定义；migrateEntity 会覆盖 registry，必须在迁移前捕获 older 做字段 diff
      Map<String, SchemaObject> existing = session.schema().listModels().stream()
        .collect(Collectors.toMap(SchemaObject::getName, m -> m));
      config.getSchema().forEach(obj -> {
        if (obj instanceof EntityDefinition e) {
          EntityDefinition entity = mergedRuntimeEntity(
            e, existing.get(e.getName()), preservedRuntimeModels);
          cache.put(schemaName + ":" + entity.getName(), entity);
          session.schema().migrateEntity(entity, existing.get(e.getName()));
        } else if (obj instanceof EnumDefinition e) {
          session.schema().createEnum(e);
        }
      });
      config.getData().forEach(d -> {
        for (Map<String, Object> record : d.getValues()) {
          try {
            session.dsl().mergeInto(d.getModelName()).values(record).execute();
          } catch (Exception e) {
            log.warn("Failed to insert record: {}", e.getMessage());
          }
        }
      });
    }
  }

  private EntityDefinition mergedRuntimeEntity(
    EntityDefinition incoming,
    SchemaObject older,
    Set<String> preservedRuntimeModels
  ) {
    if (!preservedRuntimeModels.contains(incoming.getName())
      || !(older instanceof EntityDefinition existingEntity)) {
      return incoming;
    }

    EntityDefinition entity = incoming.clone();
    entity.setFields(mergedFields(incoming, existingEntity));
    entity.setIndexes(mergedIndexes(incoming, existingEntity));

    entity.setAdditionalProperties(mergedAdditionalProperties(incoming, existingEntity));
    return entity;
  }

  private Map<String, Object> mergedAdditionalProperties(
    EntityDefinition incoming,
    EntityDefinition existing
  ) {
    Map<String, Object> result = new HashMap<>(incoming.getAdditionalProperties());
    existing.getAdditionalProperties().forEach((key, value) -> {
      if (value != null || !result.containsKey(key)) {
        result.put(key, value);
      }
    });
    return result;
  }

  private List<TypedField<?, ?>> mergedFields(EntityDefinition incoming, EntityDefinition existing) {
    Map<String, TypedField<?, ?>> incomingFields = incoming.getFields().stream()
      .collect(Collectors.toMap(TypedField::getName, field -> field, (left, right) -> right,
        LinkedHashMap::new));
    Set<String> fieldNames = new LinkedHashSet<>();
    List<TypedField<?, ?>> fields = existing.getFields().stream()
      .<TypedField<?, ?>>map(field -> {
        if (!fieldNames.add(field.getName())) {
          return null;
        }
        return incomingFields.getOrDefault(field.getName(), field);
      })
      .filter(Objects::nonNull)
      .toList();
    List<TypedField<?, ?>> result = new ArrayList<>(fields);
    incoming.getFields().stream()
      .filter(field -> !fieldNames.contains(field.getName()))
      .forEach(result::add);
    return result;
  }

  private List<IndexDefinition> mergedIndexes(EntityDefinition incoming, EntityDefinition existing) {
    Map<String, IndexDefinition> incomingIndexes = incoming.getIndexes().stream()
      .filter(index -> index.getName() != null)
      .collect(Collectors.toMap(IndexDefinition::getName, index -> index, (left, right) -> right,
        LinkedHashMap::new));
    Set<String> indexNames = new LinkedHashSet<>();
    List<IndexDefinition> indexes = existing.getIndexes().stream()
      .map(index -> {
        if (index.getName() != null) {
          indexNames.add(index.getName());
        }
        return index.getName() == null ? index : incomingIndexes.getOrDefault(index.getName(), index);
      })
      .toList();
    List<IndexDefinition> result = new ArrayList<>(indexes);
    incoming.getIndexes().stream()
      .filter(index -> index.getName() != null && !indexNames.contains(index.getName()))
      .forEach(result::add);
    return result;
  }

  private void loadJSONString(String schemaName, String jsonString) {
    ModelImportBundle bundle = JsonUtils.parseToObject(jsonString, ModelImportBundle.class);
    try (Session session = createFailsafeSession(schemaName)) {
      for (SchemaObject obj : bundle.getObjects()) {
        if (obj instanceof EntityDefinition e) {
          session.schema().createEntity(e);
        } else if (obj instanceof EnumDefinition e) {
          session.schema().createEnum(e);
        }
      }
      for (ModelImportBundle.ImportData d : bundle.getData()) {
        session.data().insertAll(d.getModelName(), d.getValues());
      }
    }
  }

  private void loadScript(String schemaName, String scriptName, ClassLoader classLoader) {
    try (InputStream is = classLoader.getResourceAsStream(scriptName)) {
      if (is == null) {
        log.warn("Script file not found: {}", scriptName);
        throw new RuntimeException("Script file not found: " + scriptName);
      }
      String scriptString = new String(is.readAllBytes());
      if (scriptName.endsWith(".fml")) {
        try (Session session = createFailsafeSession(schemaName)) {
          session.applyFML(scriptString);
        }
      } else if (scriptName.endsWith(".json")) {
        loadJSONString(schemaName, scriptString);
      } else {
        // unsupported script type
        log.warn("Unsupported script type: {}, must be .fml or .json", scriptName);
      }
    } catch (IOException e) {
      log.error("Failed to read import script: {}", e.getMessage(), e);
    }
  }

  public void loadScript(String schemaName, String scriptName) {
    loadScript(schemaName, scriptName, this.getClass().getClassLoader());
  }

  public List<String> getSchemaNames() {
    return List.copyOf(schemaProviders.keySet());
  }

  public boolean isSchemaExists(String schemaName) {
    return schemaProviders.containsKey(schemaName);
  }

  public List<SchemaObject> getModels(String schemaName) {
    return modelRegistry.listRegistered(schemaName);
  }

  public Cache getCache() {
    return cache;
  }

  public static Builder builder() {
    return new Builder();
  }

  public void registerSchemaProvider(SchemaProvider schemaProvider) {
    schemaProviders.put(schemaProvider.getName(), schemaProvider);
  }

  public SchemaProvider getSchemaProvider(String schemaName) {
    return schemaProviders.get(schemaName);
  }

  public void unregisterSchemaProvider(String schemaName) {
    schemaProviders.remove(schemaName);
  }

  public Session createSession() {
    return createSession(defaultSchemaProvider.getName());
  }

  public Session createFailsefeSession() {
    return createFailsafeSession(defaultSchemaProvider.getName());
  }

  /**
   * 按选项创建会话：隔离级别 + 方言连接准备（PRAGMA 等）。
   */
  public Session createSession(String schemaName) {
    return createSession(schemaName, SessionOptions.none());
  }

  public Session createSession(String schemaName, SessionOptions options) {
    if (!isSchemaExists(schemaName)) {
      throw new SchemaNotFoundException("Schema not found: " + schemaName);
    }
    if (failsafe) {
      return createFailsafeSession(schemaName, options);
    }
    return switch (schemaProviders.get(schemaName)) {
      case JdbcSchemaProvider jdbc -> createJdbcSession(schemaName, jdbc, false, options);
      case MongoSchemaProvider mongodb -> createMongoSession(schemaName, mongodb, false);
      case null, default -> throw new IllegalStateException("Unexpected schemaName: " + schemaName);
    };
  }

  public Session createFailsafeSession(String id) {
    return createFailsafeSession(id, SessionOptions.none());
  }

  public Session createFailsafeSession(String id, SessionOptions options) {
    return switch (schemaProviders.get(id)) {
      case JdbcSchemaProvider jdbc -> createJdbcSession(id, jdbc, true, options);
      case MongoSchemaProvider mongodb -> createMongoSession(id, mongodb, true);
      case null, default -> throw new IllegalStateException("Unexpected schemaName: " + id);
    };
  }

  private Session createJdbcSession(String schemaName, JdbcSchemaProvider jdbc, boolean failsafeMode, SessionOptions options) {
    try {
      Connection connection = jdbc.dataSource().getConnection();
      SqlContext sqlContext = new SqlContext(schemaName, new NamedParameterSqlExecutor(connection), modelRegistry, this);
      if (failsafeMode) {
        sqlContext.setFailsafe(true);
      }
      if (options.skipWriteInterceptors()) {
        sqlContext.setSkipWriteInterceptors(true);
      }
      applySessionOptions(connection, sqlContext.getSqlDialect(), options);
      DataService eventAwareDataService = new EventAwareDataService(
        new SqlDataService(sqlContext), eventPublisher, schemaName, UUID.randomUUID().toString(), this
      );
      return new SqlSession(sqlContext, eventAwareDataService);
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  private void applySessionOptions(Connection connection, SqlDialect dialect, SessionOptions options) {
    try {
      if (options != null && options.applyIsolation()) {
        if (!connection.getMetaData().supportsTransactionIsolationLevel(options.isolation())) {
          // SQLite 等数据源不支持 READ_COMMITTED：保留默认隔离级别（多为 SERIALIZABLE，语义更强），仅告警
          log.warn("Datasource does not support isolation level {}, keeping default", options.isolation());
        } else {
          connection.setTransactionIsolation(options.isolation());
        }
      }
      dialect.prepareConnection(connection);
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  private Session createMongoSession(String schemaName, MongoSchemaProvider mongodb, boolean failsafeMode) {
    MongoContext mongoContext = new MongoContext(schemaName, mongodb.mongoDatabase(), modelRegistry, this);
    if (failsafeMode) {
      mongoContext.setFailsafe(true);
    }
    return new MongoSession(mongoContext);
  }

  public UnitOfWork unitOfWork() {
    return new UnitOfWork(this);
  }

  public List<WriteInterceptor> getWriteInterceptors() {
    return writeInterceptors;
  }

  public static class Builder {
    private Cache cache;
    private SchemaProvider defaultSchemaProvider = null;
    private final List<SchemaProvider> schemaProviders = new ArrayList<>();
    private final List<WriteInterceptor> writeInterceptors = new ArrayList<>();
    private boolean failsafe = false;
    private EventPublisher eventPublisher = null;

    Builder() {
    }

    public Builder setCache(Cache cache) {
      this.cache = cache;
      return this;
    }

    public Builder setDefaultSchemaProvider(SchemaProvider schemaProvider) {
      this.defaultSchemaProvider = schemaProvider;
      return this;
    }

    public Builder registerSchemaProvider(SchemaProvider schemaProvider) {
      this.schemaProviders.add(schemaProvider);
      return this;
    }

    public Builder setFailsafe(boolean failsafe) {
      this.failsafe = failsafe;
      return this;
    }

    public Builder setEventPublisher(EventPublisher eventPublisher) {
      this.eventPublisher = eventPublisher;
      return this;
    }

    public Builder addWriteInterceptor(WriteInterceptor interceptor) {
      this.writeInterceptors.add(interceptor);
      return this;
    }

    public SessionFactory build() {
      if (defaultSchemaProvider == null) {
        throw new IllegalStateException("Please set defaultSchemaProvider");
      }
      if (cache == null) {
        this.cache = new ConcurrentHashMapCache();
      }
      return new SessionFactory(defaultSchemaProvider, schemaProviders, cache, failsafe, eventPublisher, writeInterceptors);
    }
  }

  public MemoryScriptManager getMemoryScriptManager() {
    return memoryScriptManager;
  }

  /**
   * 直接注册 BuildItem 实例，绕过 SPI ServiceLoader 机制。
   * <p>
   * 在 GraalVM 原生镜像中，ServiceLoader 的 SPI 服务注册机制可能不可靠。
   * 此方法允许调用方直接传入已实例化的 BuildItem 对象，
   * 避免对 ServiceLoader 的依赖。
   * </p>
   *
   * @param buildItem 要注册的 BuildItem 实例
   */
  public void registerBuildItem(BuildItem buildItem) {
    memoryScriptManager.loadScriptFromBuildItem(buildItem);
    // 立即将加载的 schema 应用到缓存和数据库
    // 因为 processBuildItem() 在构造函数中已执行过，此方法调用时
    // buildItem 是新追加的，需要独立触发应用逻辑
    applyBuildItemSchemas(buildItem.getSchemaName(), buildItem.getPreservedRuntimeModels());
  }

  public String getDefaultSchema() {
    return defaultSchemaProvider.getName();
  }

  public ModelRegistry getModelRegistry() {
    return modelRegistry;
  }

  public EventPublisher getEventPublisher() {
    return eventPublisher;
  }

}
