package dev.flexmodel.common.config;

import com.zaxxer.hikari.HikariDataSource;
import dev.flexmodel.connect.SessionDatasource;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Produces;
import lombok.extern.slf4j.Slf4j;
import dev.flexmodel.common.AuditDataEventListener;
import dev.flexmodel.realtime.RealtimeEventListener;
import dev.flexmodel.scheduling.TriggerDataChangedEventListener;
import dev.flexmodel.codegen.entity.Branch;
import dev.flexmodel.codegen.entity.Project;
import dev.flexmodel.project.BranchRepository;
import dev.flexmodel.project.ProjectService;
import dev.flexmodel.session.SessionFactory;
import dev.flexmodel.common.FlexmodelConfig;
import dev.flexmodel.sql.JdbcSchemaProvider;

import java.util.List;

/**
 * @author cjbi
 */
@ApplicationScoped
@Slf4j
public class SessionConfig {

  public static final String SYSTEM_DS_KEY = "system";

  public void installDatasource(@Observes StartupEvent startupEvent,
                                ProjectService projectService, SessionDatasource sessionDatasource,
                                BranchRepository branchRepository) {
    long beginTime = System.currentTimeMillis();
    List<Project> projects = projectService.findProjects();
    for (Project project : projects) {
      sessionDatasource.add(project);
      // 注册非 main 分支的数据库 SchemaProvider
      List<Branch> branches = branchRepository.findByProjectId(project.getId());
      for (Branch branch : branches) {
        sessionDatasource.registerSchema(branch.getDatabaseName());
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
    HikariDataSource ds = new HikariDataSource();
    ds.setJdbcUrl(config.url());
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

}
