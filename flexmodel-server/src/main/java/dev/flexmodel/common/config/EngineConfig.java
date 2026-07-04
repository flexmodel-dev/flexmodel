package dev.flexmodel.common.config;

import com.mysql.cj.jdbc.MysqlDataSource;
import com.zaxxer.hikari.HikariDataSource;
import dev.flexmodel.codegen.entity.Branch;
import dev.flexmodel.codegen.entity.Project;
import dev.flexmodel.common.AuditDataEventListener;
import dev.flexmodel.common.FlexmodelConfig;
import dev.flexmodel.common.SchemaRegistry;
import dev.flexmodel.project.BranchRepository;
import dev.flexmodel.project.ProjectService;
import dev.flexmodel.realtime.RealtimeEventListener;
import dev.flexmodel.scheduling.TriggerDataChangedEventListener;
import dev.flexmodel.session.SessionFactory;
import dev.flexmodel.sql.JdbcSchemaProvider;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Produces;
import lombok.extern.slf4j.Slf4j;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.io.File;
import java.util.List;

/**
 * @author cjbi
 */
@ApplicationScoped
@Slf4j
public class EngineConfig {

  public static final String SYSTEM_DS_KEY = "system";

  public void installDatasource(@Observes StartupEvent startupEvent,
                                ProjectService projectService, SchemaRegistry schemaRegistry,
                                BranchRepository branchRepository) {
    long beginTime = System.currentTimeMillis();
    List<Project> projects = projectService.findProjects();
    for (Project project : projects) {
      schemaRegistry.add(project);
      // 注册非 main 分支的数据库 SchemaProvider
      List<Branch> branches = branchRepository.findByProjectId(project.getId());
      for (Branch branch : branches) {
        schemaRegistry.registerSchema(branch.getDatabaseName());
      }
    }
    log.info("========== Engine init successful in {} ms!", System.currentTimeMillis() - beginTime);
  }

  @Produces
  @ApplicationScoped
  public SessionFactory sessionFactory(FlexmodelConfig flexmodelConfig,
                                       TriggerDataChangedEventListener triggerDataChangedEventListener,
                                       AuditDataEventListener auditDataEventListener,
                                       RealtimeEventListener realtimeEventListener) {
    FlexmodelConfig.DatasourceConfig datasourceConfig = flexmodelConfig.datasources().get(SYSTEM_DS_KEY);
    HikariDataSource defaultDs = createDataSource(datasourceConfig);
    SessionFactory.Builder builder = SessionFactory.builder()
      .setDefaultSchemaProvider(new JdbcSchemaProvider(SYSTEM_DS_KEY, defaultDs))
      .setFailsafe(true);
    flexmodelConfig.datasources().forEach((key, value) -> {
      if (key.equals(SYSTEM_DS_KEY)) {
        return;
      }
      HikariDataSource ds = createDataSource(value);
      builder.registerSchemaProvider(new JdbcSchemaProvider(key, ds));
    });
    SessionFactory sf = builder.build();
    sf.getEventPublisher().addListener(triggerDataChangedEventListener);
    sf.getEventPublisher().addListener(auditDataEventListener);
    sf.getEventPublisher().addListener(realtimeEventListener);
    return sf;
  }

  /**
   * 创建优化配置的 HikariDataSource，包含连接泄漏检测和合理的超时设置。
   * <p>
   * 在 native image 中，HikariCP 不能通过 DriverManager 的 SPI 自动查找驱动，
   * 因此直接创建底层 DataSource（如 SQLiteDataSource）并传给 HikariCP，
   * 完全绕过 DriverManager。
   */
  static HikariDataSource createDataSource(FlexmodelConfig.DatasourceConfig config) {
    ensureSqliteParentDir(config.url());
    HikariDataSource ds = new HikariDataSource();
    // 直接设置底层 DataSource，绕过 DriverManager
    DataSource underlyingDs = createUnderlyingDataSource(config);
    if (underlyingDs != null) {
      ds.setDataSource(underlyingDs);
    } else {
      ds.setJdbcUrl(config.url());
    }
    config.username().ifPresent(ds::setUsername);
    config.password().ifPresent(ds::setPassword);
    // 连接池最大连接数
    ds.setMaximumPoolSize(10);
    // 获取连接超时：10 秒（默认 30 秒），快速失败而非长时间阻塞
    ds.setConnectionTimeout(10000);
    // 连接最大存活时间：10 分钟（默认 30 分钟），防止数据库侧断开后连接池未感知
    ds.setMaxLifetime(600000);
    // 空闲超时：5 分钟，及时回收空闲连接
    ds.setIdleTimeout(300000);
    // 连接泄漏检测：60 秒，超过此时间的活动连接会在日志中输出警告
    ds.setLeakDetectionThreshold(60000);
    // 连接验证超时：3 秒
    ds.setValidationTimeout(3000);
    return ds;
  }

  /**
   * 根据 JDBC URL 创建对应的底层 DataSource。
   * 在 native image 中直接实例化驱动厂商的 DataSource，
   * 绕过 DriverManager 的 SPI 自动注册机制。
   */
  public static DataSource createUnderlyingDataSource(String url, String username, String password) {
    if (url == null) {
      return null;
    }
    if (url.startsWith("jdbc:sqlite:")) {
      SQLiteDataSource sqliteDs = new SQLiteDataSource();
      sqliteDs.setUrl(url);
      return sqliteDs;
    }
    if (url.startsWith("jdbc:mysql:") || url.startsWith("jdbc:mariadb:")) {
      MysqlDataSource mysqlDs = new MysqlDataSource();
      mysqlDs.setUrl(url);
      if (username != null) mysqlDs.setUser(username);
      if (password != null) mysqlDs.setPassword(password);
      return mysqlDs;
    }
    return null;
  }

  private static DataSource createUnderlyingDataSource(FlexmodelConfig.DatasourceConfig config) {
    return createUnderlyingDataSource(config.url(),
      config.username().orElse(null), config.password().orElse(null));
  }

  /**
   * 如果是 SQLite 文件数据库，确保父目录存在。
   */
  public static void ensureSqliteParentDir(String jdbcUrl) {
    if (jdbcUrl != null && jdbcUrl.startsWith("jdbc:sqlite:file:")) {
      String filePath = jdbcUrl.substring("jdbc:sqlite:file:".length());
      int queryIdx = filePath.indexOf('?');
      if (queryIdx > 0) {
        filePath = filePath.substring(0, queryIdx);
      }
      if (!":memory:".equals(filePath)) {
        File parentDir = new File(filePath).getParentFile();
        if (parentDir != null && !parentDir.exists()) {
          parentDir.mkdirs();
        }
      }
    }
  }

}
