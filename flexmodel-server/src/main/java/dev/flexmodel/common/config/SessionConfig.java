package dev.flexmodel.common.config;

import com.zaxxer.hikari.HikariDataSource;
import dev.flexmodel.connect.SessionDatasource;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Produces;
import lombok.extern.slf4j.Slf4j;
import dev.flexmodel.common.AuditDataEventListener;
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
                                       AuditDataEventListener auditDataEventListener) {
    FlexmodelConfig.DatasourceConfig datasourceConfig = flexmodelConfig.datasources().get(SYSTEM_DS_KEY);
    HikariDataSource defaultDs = new HikariDataSource();
    defaultDs.setMaxLifetime(30000);
    defaultDs.setJdbcUrl(datasourceConfig.url());
    defaultDs.setUsername(datasourceConfig.username().orElse(null));
    defaultDs.setPassword(datasourceConfig.password().orElse(null));
    SessionFactory.Builder builder = SessionFactory.builder()
      .setDefaultSchemaProvider(new JdbcSchemaProvider(SYSTEM_DS_KEY, defaultDs))
      .setFailsafe(true);
    flexmodelConfig.datasources().forEach((key, value) -> {
      if (key.equals(SYSTEM_DS_KEY)) {
        return;
      }
      HikariDataSource ds = new HikariDataSource();
      ds.setMaxLifetime(30000);
      ds.setJdbcUrl(value.url());
      ds.setUsername(value.username().orElse(null));
      ds.setPassword(value.password().orElse(null));
      builder.registerSchemaProvider(new JdbcSchemaProvider(key, ds));
    });
    SessionFactory sf = builder.build();
    sf.getEventPublisher().addListener(triggerDataChangedEventListener);
    sf.getEventPublisher().addListener(auditDataEventListener);
    return sf;
  }

}
