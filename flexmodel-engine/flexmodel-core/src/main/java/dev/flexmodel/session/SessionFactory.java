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
import dev.flexmodel.model.SchemaObject;
import dev.flexmodel.mongodb.MongoContext;
import dev.flexmodel.mongodb.MongoSchemaProvider;
import dev.flexmodel.mongodb.MongoSession;
import dev.flexmodel.service.DataService;
import dev.flexmodel.service.EventAwareDataService;
import dev.flexmodel.sql.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.util.*;

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

  SessionFactory(SchemaProvider defaultSchemaProvider, List<SchemaProvider> schemaProviders, Cache cache, boolean failsafe, EventPublisher eventPublisher) {
    this.cache = cache;
    this.memoryScriptManager = new MemoryScriptManager();
    this.eventPublisher = eventPublisher != null ? eventPublisher : new SimpleEventPublisher();
    this.defaultSchemaProvider = defaultSchemaProvider;
    registerSchemaProvider(defaultSchemaProvider);
    schemaProviders.forEach(this::registerSchemaProvider);
    this.modelRegistry = initializeModelRegistry(defaultSchemaProvider);
    this.failsafe = failsafe;
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
   */
  private void applyBuildItemSchemas(String schemaName) {
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

    try (Session session = createFailsafeSession(schemaName)) {
      config.getSchema().forEach(obj -> {
        if (obj instanceof EntityDefinition e) {
          session.schema().createEntity(e);
        } else if (obj instanceof EnumDefinition e) {
          session.schema().createEnum(e);
        }
      });
      config.getData().forEach(d -> {
        for (Map<String, Object> record : d.getValues()) {
          try {
            session.dsl().mergeInto(d.getModelName()).values(record).execute();
          } catch (Exception e) {
            log.error("Failed to insert record: {}", e.getMessage(), e);
          }
        }
      });
    }
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
   * 宽松模式:
   * 允许ddl语句的错误
   *
   * @param id
   * @return
   */
  public Session createFailsafeSession(String id) {
    try {
      return switch (schemaProviders.get(id)) {
        case JdbcSchemaProvider jdbc -> {
          Connection connection = jdbc.dataSource().getConnection();
          SqlContext sqlContext = new SqlContext(id, new NamedParameterSqlExecutor(connection), modelRegistry, this);
          sqlContext.setFailsafe(true);

          DataService originalDataService = new SqlDataService(sqlContext);

          String sessionId = UUID.randomUUID().toString();
          DataService eventAwareDataService = new EventAwareDataService(
            originalDataService, eventPublisher, id, sessionId, this
          );

          yield new SqlSession(sqlContext, eventAwareDataService);
        }
        case MongoSchemaProvider mongodb -> {
          MongoDatabase mongoDatabase = mongodb.mongoDatabase();
          MongoContext mongoContext = new MongoContext(id, mongoDatabase, modelRegistry, this);
          mongoContext.setFailsafe(true);

          yield new MongoSession(mongoContext);
        }
        case null,
             default -> throw new IllegalStateException("Unexpected schemaName: " + id);
      };
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public Session createSession(String schemaName) {
    if (!isSchemaExists(schemaName)) {
      throw new SchemaNotFoundException("Schema not found: " + schemaName);
    }
    try {
      if (failsafe) {
        return createFailsafeSession(schemaName);
      }
      return switch (schemaProviders.get(schemaName)) {
        case JdbcSchemaProvider jdbc -> {
          Connection connection = jdbc.dataSource().getConnection();
          SqlContext sqlContext = new SqlContext(schemaName, new NamedParameterSqlExecutor(connection), modelRegistry, this);

          DataService originalDataService = new SqlDataService(sqlContext);

          String sessionId = UUID.randomUUID().toString();
          DataService eventAwareDataService = new EventAwareDataService(
            originalDataService, eventPublisher, schemaName, sessionId, this
          );

          yield new SqlSession(sqlContext, eventAwareDataService);
        }
        case MongoSchemaProvider mongodb -> {
          MongoDatabase mongoDatabase = mongodb.mongoDatabase();
          MongoContext mongoContext = new MongoContext(schemaName, mongoDatabase, modelRegistry, this);

          yield new MongoSession(mongoContext);
        }
        case null,
             default -> throw new IllegalStateException("Unexpected schemaName: " + schemaName);
      };
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static class Builder {
    private Cache cache;
    private SchemaProvider defaultSchemaProvider = null;
    private final List<SchemaProvider> schemaProviders = new ArrayList<>();
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

    public SessionFactory build() {
      if (defaultSchemaProvider == null) {
        throw new IllegalStateException("Please set defaultSchemaProvider");
      }
      if (cache == null) {
        this.cache = new ConcurrentHashMapCache();
      }
      return new SessionFactory(defaultSchemaProvider, schemaProviders, cache, failsafe, eventPublisher);
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
    applyBuildItemSchemas(buildItem.getSchemaName());
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
