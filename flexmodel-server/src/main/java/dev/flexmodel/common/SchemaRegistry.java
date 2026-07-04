package dev.flexmodel.common;

import com.zaxxer.hikari.HikariDataSource;
import dev.flexmodel.SchemaProvider;
import dev.flexmodel.codegen.entity.Project;
import dev.flexmodel.common.config.EngineConfig;
import dev.flexmodel.common.utils.StringUtils;
import dev.flexmodel.project.ProjectService;
import dev.flexmodel.session.Session;
import dev.flexmodel.session.SessionFactory;
import dev.flexmodel.sql.JdbcSchemaProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Schema 注册中心，管理项目 Schema 与 SessionFactory 之间的生命周期绑定。
 * <p>
 * 核心职责：
 * <ul>
 *   <li>注册/注销 SchemaProvider 到 SessionFactory</li>
 *   <li>项目创建/删除时的 Schema 初始化与清理</li>
 *   <li>物理表名查询与原生 SQL 执行</li>
 * </ul>
 *
 * @author cjbi
 */
@ApplicationScoped
@Slf4j
public class SchemaRegistry {

  @Inject
  SessionFactory sessionFactory;

  @Inject
  FlexmodelConfig flexmodelConfig;

  @Inject
  ProjectService projectService;

  private String getContent(String template) {
    return StringUtils.simpleRenderTemplate(template, SystemVariablesHolder.getSystemVariables());
  }

  public List<String> getPhysicsModelNames(Project project) {
    List<String> list = new ArrayList<>();
    String databaseName = projectService.resolveDatabaseName(project.getId());
    FlexmodelConfig.DatasourceConfig datasource = flexmodelConfig.datasources().get(EngineConfig.SYSTEM_DS_KEY);
    String jdbcUrl = flexmodelConfig.projectUrlTemplate().replace("{{databaseName}}", databaseName);
    String username = datasource.username().orElse(null);
    String password = datasource.password().orElse(null);
    try (var conn = DriverManager.getConnection(jdbcUrl, username, password);
        ResultSet tables = conn.getMetaData().getTables(null, null, "%", new String[] { "TABLE" })) {
      while (tables.next()) {
        String tableName = tables.getString("TABLE_NAME");
        list.add(tableName);
      }
      return list;
    } catch (SQLException e) {
      return list;
    }
  }

  public void add(Project project) {
    registerSchema(projectService.resolveDatabaseName(project.getId()));
  }

  /**
   * 注册指定 Schema 的 SchemaProvider 到 SessionFactory。
   *
   * @param schemaName Schema 名称
   */
  public void registerSchema(String schemaName) {
    try {
      String actualSchemaName = "system".equals(schemaName) ? "flexmodel" : schemaName;
      // 如果配置文件中有该数据源的显式配置，使用配置中的URL而非模板URL
      FlexmodelConfig.DatasourceConfig configDs = flexmodelConfig.datasources().get(schemaName);
      if (configDs != null) {
        // 配置文件中已定义该数据源，如果SchemaProvider已注册则跳过
        if (sessionFactory.isSchemaExists(actualSchemaName)) {
          log.info("SchemaProvider '{}' already registered from config, skipping dynamic registration",
              actualSchemaName);
          return;
        }
        // 配置存在但SchemaProvider未注册，使用配置URL创建
        HikariDataSource ds = buildOptimizedDataSource(configDs.url(), configDs.username().orElse(null), configDs.password().orElse(null));
        SchemaProvider schemaProvider = new JdbcSchemaProvider(actualSchemaName, ds);
        sessionFactory.registerSchemaProvider(schemaProvider);
        log.info("Registered SchemaProvider '{}' from config URL: {}", actualSchemaName, configDs.url());
      } else {
        // 配置文件中没有显式配置，使用projectUrlTemplate动态创建
        SchemaProvider schemaProvider = new JdbcSchemaProvider(actualSchemaName, buildJdbcDataSource(schemaName));
        sessionFactory.registerSchemaProvider(schemaProvider);
        log.info("Registered SchemaProvider '{}' from template URL", actualSchemaName);
      }
    } catch (Exception e) {
      log.error("Session dataSource create error: {}", e.getMessage(), e);
    }
  }

  /**
   * 取消注册指定 Schema 的 SchemaProvider。
   *
   * @param schemaName Schema 名称
   */
  public void unregisterSchema(String schemaName) {
    // 取消注册前关闭对应的 HikariDataSource
    try {
      dev.flexmodel.SchemaProvider sp = sessionFactory.getSchemaProvider(schemaName);
      if (sp instanceof JdbcSchemaProvider jsp) {
        jsp.dataSource().unwrap(HikariDataSource.class).close();
      }
    } catch (Exception e) {
      log.warn("Failed to close HikariDataSource for schema '{}'", schemaName, e);
    }
    sessionFactory.unregisterSchemaProvider(schemaName);
    log.info("Unregistered SchemaProvider '{}'", schemaName);
  }

  /**
   * 清理指定 Schema 在 f_model_registry 表中的模型注册记录。
   * <p>
   * 该方法操作的是系统数据源中的 f_model_registry 表，与项目自身的物理 Schema 相互独立，
   * 因此可以在 unregisterSchema / dropSchema 之前或之后安全调用。
   *
   * @param schemaName Schema 名称（即项目的 databaseName）
   */
  public void unregisterModels(String schemaName) {
    try {
      sessionFactory.getModelRegistry().unregisterAll(schemaName);
      log.info("Unregistered model registry records for schema '{}'", schemaName);
    } catch (Exception e) {
      log.warn("Failed to unregister model registry records for schema '{}'", schemaName, e);
    }
  }

  public DataSource buildJdbcDataSource(String databaseName) {
    FlexmodelConfig.DatasourceConfig datasource = flexmodelConfig.datasources().get("system");
    String jdbcUrl = flexmodelConfig.projectUrlTemplate().replace("{{databaseName}}", databaseName);
    String username = datasource.username().orElse(null);
    String password = datasource.password().orElse(null);
    return buildOptimizedDataSource(getContent(jdbcUrl), getContent(username), getContent(password));
  }

  /**
   * 创建优化配置的 HikariDataSource，统一连接池参数。
   */
  private HikariDataSource buildOptimizedDataSource(String jdbcUrl, String username, String password) {
    dev.flexmodel.common.config.EngineConfig.ensureSqliteParentDir(jdbcUrl);
    HikariDataSource ds = new HikariDataSource();
    // 直接设置底层 DataSource，绕过 DriverManager（native image 中 SPI 被禁用）
    javax.sql.DataSource underlyingDs =
      dev.flexmodel.common.config.EngineConfig.createUnderlyingDataSource(jdbcUrl, username, password);
    if (underlyingDs != null) {
      ds.setDataSource(underlyingDs);
    } else {
      ds.setJdbcUrl(jdbcUrl);
    }
    if (username != null) ds.setUsername(username);
    if (password != null) ds.setPassword(password);
    ds.setMaximumPoolSize(10);
    ds.setConnectionTimeout(10000);
    ds.setMaxLifetime(600000);
    ds.setIdleTimeout(300000);
    ds.setLeakDetectionThreshold(60000);
    ds.setValidationTimeout(3000);
    return ds;
  }

  public void delete(Project project) {
    sessionFactory.unregisterSchemaProvider(projectService.resolveDatabaseName(project.getId()));
  }

  @SuppressWarnings("all")
  public NativeQueryResult executeNativeQuery(Project project, String statement, Map<String, Object> parameters) {
    try (Session session = sessionFactory.createSession(projectService.resolveDatabaseName(project.getId()))) {
      long beginTime = System.currentTimeMillis();
      Object result = session.data().executeNative(statement, parameters);
      long endTime = System.currentTimeMillis() - beginTime;
      return new NativeQueryResult(endTime, result);
    }
  }

}
