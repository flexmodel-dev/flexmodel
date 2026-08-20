package dev.flexmodel.common.config;

import dev.flexmodel.codegen.entity.Branch;
import dev.flexmodel.codegen.entity.Project;
import dev.flexmodel.common.AuditDataEventListener;
import dev.flexmodel.common.FlexmodelConfig;
import dev.flexmodel.common.SchemaRegistry;
import dev.flexmodel.project.BranchRepository;
import dev.flexmodel.project.ProjectService;
import dev.flexmodel.realtime.RealtimeEventListener;
import dev.flexmodel.realtime.RealtimeRabbitmqListener;
import dev.flexmodel.scheduling.TriggerDataChangedEventListener;
import dev.flexmodel.session.SessionFactory;
import dev.flexmodel.sql.JdbcSchemaProvider;
import dev.flexmodel.test.codegen.DevTest;
import io.agroal.api.AgroalDataSource;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Produces;
import lombok.extern.slf4j.Slf4j;

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
      // 娉ㄥ唽闈?main 鍒嗘敮鐨勬暟鎹簱 SchemaProvider
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
                                       RealtimeEventListener realtimeEventListener,
                                       RealtimeRabbitmqListener realtimeRabbitmqListener) {
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
    // 鐩存帴娉ㄥ唽 BuildItem 瀹炰緥锛岀粫杩?SPI ServiceLoader 鏈哄埗
    // 鍦?GraalVM 鍘熺敓闀滃儚涓?ServiceLoader.load() 鏃犳硶姝ｇ‘鍙戠幇 SPI 瀹炵幇绫?
    sf.registerBuildItem(new dev.flexmodel.codegen.System());
    sf.registerBuildItem(new DevTest());
    sf.getEventPublisher().addListener(triggerDataChangedEventListener);
    sf.getEventPublisher().addListener(auditDataEventListener);
    sf.getEventPublisher().addListener(realtimeEventListener);
    sf.getEventPublisher().addListener(realtimeRabbitmqListener);
    return sf;
  }

  /**
   * 濡傛灉鏄?SQLite 鏂囦欢鏁版嵁搴擄紝纭繚鐖剁洰褰曞瓨鍦ㄣ€?
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
