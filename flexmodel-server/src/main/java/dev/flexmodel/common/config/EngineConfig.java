package dev.flexmodel.common.config;

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

import java.io.File;
import java.util.List;
import java.util.Optional;

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
   */
  static HikariDataSource createDataSource(FlexmodelConfig.DatasourceConfig config) {
    ensureSqliteParentDir(config.url());
    HikariDataSource ds = new HikariDataSource();
    ds.setJdbcUrl(config.url());
    // 显式指定 JDBC 驱动类，避免在 native image 中依赖 DriverManager 的 SPI 自动注册
    // （GraalVM native image 默认禁用 ServiceLoader，DriverManager.getDriver() 会返回 null）
    resolveDriverClassName(config.url()).ifPresent(ds::setDriverClassName);
    ds.setUsername(config.username().orElse(null));
    ds.setPassword(config.password().orElse(null));
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

  /**
   * 根据 JDBC URL 解析对应的驱动类名。
   * 在 native image 中，HikariCP 不能通过 DriverManager 的 SPI 自动查找驱动，
   * 需要显式设置 driverClassName 让 HikariCP 直接加载驱动类。
   */
  public static Optional<String> resolveDriverClassName(String jdbcUrl) {
    if (jdbcUrl == null) {
      return Optional.empty();
    }
    if (jdbcUrl.startsWith("jdbc:sqlite:")) {
      return Optional.of("org.sqlite.JDBC");
    }
    if (jdbcUrl.startsWith("jdbc:mysql:") || jdbcUrl.startsWith("jdbc:mariadb:")) {
      return Optional.of("com.mysql.cj.jdbc.Driver");
    }
    if (jdbcUrl.startsWith("jdbc:postgresql:")) {
      return Optional.of("org.postgresql.Driver");
    }
    if (jdbcUrl.startsWith("jdbc:oracle:")) {
      return Optional.of("oracle.jdbc.OracleDriver");
    }
    if (jdbcUrl.startsWith("jdbc:sqlserver:")) {
      return Optional.of("com.microsoft.sqlserver.jdbc.SQLServerDriver");
    }
    return Optional.empty();
  }

}
