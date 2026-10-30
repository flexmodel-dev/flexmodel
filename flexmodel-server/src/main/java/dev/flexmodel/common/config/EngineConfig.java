package dev.flexmodel.common.config;

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
import dev.flexmodel.system.SystemBuildItem;
import dev.flexmodel.test.codegen.DevTest;
import io.agroal.api.AgroalDataSource;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Produces;
import jakarta.interceptor.Interceptor;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author cjbi
 */
@ApplicationScoped
@Slf4j
public class EngineConfig {

  public static final String SYSTEM_DS_KEY = "system";

  public void installDatasource(@Observes @Priority(Interceptor.Priority.APPLICATION) StartupEvent startupEvent,
                                ProjectService projectService, SchemaRegistry schemaRegistry,
                                BranchRepository branchRepository) {
    long beginTime = System.currentTimeMillis();
    List<Project> projects = projectService.findProjects();
    Set<String> schemas = new LinkedHashSet<>();
    for (Project project : projects) {
      schemas.add(project.getDatabaseName());
      List<Branch> branches = branchRepository.findByProjectId(project.getId());
      for (Branch branch : branches) {
        schemas.add(branch.getDatabaseName());
      }
    }
    // registerSchema 仅注册项目业务库的数据源；平台级表统一存于 system schema
    schemas.forEach(schemaRegistry::registerSchema);
    log.info("========== Engine init successful in {} ms!", System.currentTimeMillis() - beginTime);
  }

  @Produces
  @ApplicationScoped
  public SessionFactory sessionFactory(FlexmodelConfig flexmodelConfig,
                                       TriggerDataChangedEventListener triggerDataChangedEventListener,
                                       AuditDataEventListener auditDataEventListener,
                                       RealtimeEventListener realtimeEventListener) {
    FlexmodelConfig.DatasourceConfig datasourceConfig = flexmodelConfig.datasources().get(SYSTEM_DS_KEY);
    AgroalDataSource defaultDs = AgroalDataSourceFactory.createDataSource(
      datasourceConfig.url(),
      datasourceConfig.username().orElse(null),
      datasourceConfig.password().orElse(null));
    SessionFactory.Builder builder = SessionFactory.builder()
      .setDefaultSchemaProvider(new JdbcSchemaProvider(SYSTEM_DS_KEY, defaultDs))
      .setFailsafe(true);
    flexmodelConfig.datasources().forEach((key, value) -> {
      if (key.equals(SYSTEM_DS_KEY)) {
        return;
      }
      log.info("Registering datasource: key={}, url={}", key, value.url());
      AgroalDataSource ds = AgroalDataSourceFactory.createDataSource(
        value.url(),
        value.username().orElse(null),
        value.password().orElse(null));
      builder.registerSchemaProvider(new JdbcSchemaProvider(key, ds));
    });
    log.info("Total registered datasources: {}", flexmodelConfig.datasources().keySet());
    SessionFactory sf = builder.build();
    // 直接注册 BuildItem 实例，绕过 SPI ServiceLoader 机制
    // 在 GraalVM 原生镜像中 ServiceLoader.load() 无法正确发现 SPI 实现类
    sf.registerBuildItem(new SystemBuildItem());
    sf.registerBuildItem(new DevTest());
    sf.getEventPublisher().addListener(triggerDataChangedEventListener);
    sf.getEventPublisher().addListener(auditDataEventListener);
    sf.getEventPublisher().addListener(realtimeEventListener);
    return sf;
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
