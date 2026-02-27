package dev.flexmodel.session;

import com.mongodb.client.MongoDatabase;
import dev.flexmodel.sql.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import dev.flexmodel.SchemaProvider;
import dev.flexmodel.JsonUtils;
import dev.flexmodel.ModelImportBundle;
import dev.flexmodel.ModelRegistry;
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
import dev.flexmodel.parser.ASTNodeConverter;
import dev.flexmodel.parser.impl.ModelParser;
import dev.flexmodel.parser.impl.ParseException;
import dev.flexmodel.service.DataService;
import dev.flexmodel.service.EventAwareDataService;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
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

  SessionFactory(SchemaProvider defaultSchemaProvider, List<SchemaProvider> schemaProviders, Cache cache, boolean failsafe, EventPublisher eventPublisher) {
    this.cache = cache;
    this.memoryScriptManager = new MemoryScriptManager();
    this.eventPublisher = eventPublisher != null ? eventPublisher : new SimpleEventPublisher();
    this.defaultSchemaProvider = defaultSchemaProvider;
    registerSchemaProvider(defaultSchemaProvider);
    schemaProviders.forEach(this::registerSchemaProvider);
    this.modelRegistry = initializeModelRegistry(defaultSchemaProvider);
    this.failsafe = failsafe;
    processBuildItem();
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
  void processBuildItem() {
    // 将BuildItem脚本加载到内存中
    memoryScriptManager.loadScriptsFromBuildItems();

    // 将内存中的脚本应用到缓存中
    memoryScriptManager.getSchemaNames().forEach(schemaName -> {
      MemoryScriptManager.SchemaScriptConfig config = memoryScriptManager.getScriptConfig(schemaName);
      config.getSchema().forEach(model -> cache.put(schemaName + ":" + model.getName(), model));
      try (Session session = createFailsafeSession(schemaName)) {
        processModels(config.getSchema(), session);
        processImportData(config.getData(), session);
      }
    });
  }

  public void loadIDLString(String schemaName, String idlString) {
    try {
      // 处理空字符串或只包含空白字符的字符串
      if (idlString == null || idlString.trim().isEmpty()) {
        log.info("Empty or null IDL string provided for schema: {}", schemaName);
        return;
      }

      // 创建IDL解析器
      ModelParser parser =
        new ModelParser(new java.io.StringReader(idlString));

      // 解析IDL字符串，获取AST节点列表
      List<ModelParser.ASTNode> ast = parser.CompilationUnit();

      // 将AST节点转换为SchemaObject列表
      List<SchemaObject> schema = new ArrayList<>();
      for (ModelParser.ASTNode obj : ast) {
        SchemaObject schemaObject = ASTNodeConverter.toSchemaObject(obj);
        if (schemaObject != null) {
          schema.add(schemaObject);
        } else {
          log.warn("Failed to convert AST node to SchemaObject: {}", obj);
        }
      }

      // 使用故障安全会话处理模型 - 使用默认数据源
      try (Session session = createFailsafeSession(schemaName)) {
        processModels(schema, session);
      }

      log.info("Successfully loaded {} models from IDL for schema: {}", schema.size(), schemaName);
    } catch (ParseException e) {
      log.error("Failed to parse IDL string for schema {}: {}", schemaName, e.getMessage(), e);
      throw new RuntimeException("IDL parsing failed: " + e.getMessage(), e);
    } catch (Exception e) {
      log.error("Failed to load IDL string for schema {}: {}", schemaName, e.getMessage(), e);
      throw new RuntimeException("Failed to load IDL: " + e.getMessage(), e);
    }
  }

  public void loadJSONString(String schemaName, String jsonString) {
    ModelImportBundle bundle = JsonUtils.parseToObject(jsonString, ModelImportBundle.class);
    try (Session session = createFailsafeSession(schemaName)) {
      processModels(bundle.getObjects(), session);
      processImportData(bundle.getData(), session);
    }
  }

  public void loadScript(String schemaName, String scriptName, ClassLoader classLoader) {
    try (InputStream is = classLoader.getResourceAsStream(scriptName)) {
      if (is == null) {
        log.warn("Script file not found: {}", scriptName);
        throw new RuntimeException("Script file not found: " + scriptName);
      }
      String scriptString = new String(is.readAllBytes());
      if (scriptName.endsWith(".json")) {
        loadJSONString(schemaName, scriptString);
      } else if (scriptName.endsWith(".idl")) {
        loadIDLString(schemaName, scriptString);
      } else {
        // unsupported script type
        log.warn("Unsupported script type: {}, must be .json or .idl", scriptName);
      }
    } catch (IOException e) {
      log.error("Failed to read import script: {}", e.getMessage(), e);
    }
  }

  public void loadScript(String schemaName, String scriptName) {
    loadScript(schemaName, scriptName, this.getClass().getClassLoader());
  }

  private void processModels(List<SchemaObject> models, Session session) {
    Map<String, SchemaObject> wrapperMap = session.schema().listModels().stream().collect(Collectors.toMap(SchemaObject::getName, m -> m));
    for (SchemaObject model : models) {
      SchemaObject older = wrapperMap.get(model.getName());
      if (older != null && Objects.equals(older.getIdl(), model.getIdl())) {
        continue;
      }
      if (model instanceof EntityDefinition newer) {
        try {
          updateEntity(session, newer, (EntityDefinition) older);
        } catch (Exception e) {
          log.warn("Error processing model: {}", e.getMessage(), e);
        }
      } else if (model instanceof EnumDefinition newer) {
        try {
          updateEnum(session, newer);
        } catch (Exception e) {
          log.warn("Error processing model: {}", e.getMessage(), e);
        }
      }
    }
  }

  private void updateEnum(Session session, EnumDefinition newer) {
    try {
      session.schema().dropModel(newer.getName());
      session.schema().createEnum(newer);
    } catch (Exception e) {
      log.warn("Error processing model: {}", e.getMessage(), e);
    }
  }

  private void updateEntity(Session session, EntityDefinition newer, EntityDefinition older) throws Exception {
    try {
      session.schema().createEntity(newer.clone());
    } catch (Exception e) {
      if (older != null) {
        updateEntityFields(session, newer, older);
      }
    }
  }

  private void updateEntityFields(Session session, EntityDefinition newer, EntityDefinition older) {
    newer.getFields().forEach(field -> {
      try {
        if (older.getField(field.getName()) == null) {
          session.schema().createField(field);
        } else if (!field.equals(older.getField(field.getName()))) {
          session.schema().modifyField(field);
        }
      } catch (Exception e) {
        log.warn("Error updating field: {}", e.getMessage(), e);
      }
    });
  }

  private void processImportData(List<ModelImportBundle.ImportData> data, Session session) {
    data.forEach(item -> {
      try {
        session.data().insertAll(item.getModelName(), item.getValues());
      } catch (Exception e) {
        log.warn("Error importing data: {}", e.getMessage());
      }
    });
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
