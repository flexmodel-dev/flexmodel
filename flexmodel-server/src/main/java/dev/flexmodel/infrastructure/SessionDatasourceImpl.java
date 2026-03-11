package dev.flexmodel.infrastructure;

import com.zaxxer.hikari.HikariDataSource;
import dev.flexmodel.SchemaProvider;
import dev.flexmodel.codegen.entity.Project;
import dev.flexmodel.domain.model.connect.NativeQueryResult;
import dev.flexmodel.domain.model.connect.SessionDatasource;
import dev.flexmodel.infrastructure.session.SessionConfig;
import dev.flexmodel.session.Session;
import dev.flexmodel.session.SessionFactory;
import dev.flexmodel.shared.FlexmodelConfig;
import dev.flexmodel.shared.SystemVariablesHolder;
import dev.flexmodel.shared.utils.StringUtils;
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
 * @author cjbi
 */
@ApplicationScoped
@Slf4j
public class SessionDatasourceImpl implements SessionDatasource {

  @Inject
  SessionFactory sessionFactory;

  @Inject
  FlexmodelConfig flexmodelConfig;

  private String getContent(String template) {
    return StringUtils.simpleRenderTemplate(template, SystemVariablesHolder.getSystemVariables());
  }

  @Override
  public List<String> getPhysicsModelNames(Project project) {
    List<String> list = new ArrayList<>();
    String databaseName = project.getDatabaseName();
    FlexmodelConfig.DatasourceConfig datasource = flexmodelConfig.datasources().get(SessionConfig.SYSTEM_DS_KEY);
    String jdbcUrl = flexmodelConfig.projectUrlTemplate().replace("{{databaseName}}", databaseName);
    String username = datasource.username().orElse(null);
    String password = datasource.password().orElse(null);
    try (var conn = DriverManager.getConnection(jdbcUrl, username, password)) {
      ResultSet tables = conn.getMetaData().getTables(null, null, "%", new String[]{"TABLE"});
      while (tables.next()) {
        String tableName = tables.getString("TABLE_NAME");
        list.add(tableName);
      }
      return list;
    } catch (SQLException e) {
      return list;
    }
  }

  @Override
  public void add(Project project) {
    try {
      SchemaProvider schemaProvider = new JdbcSchemaProvider(project.getDatabaseName().equals("system")? "flexmodel" : project.getDatabaseName(), buildJdbcDataSource(project.getDatabaseName()));
      sessionFactory.registerSchemaProvider(schemaProvider);
    } catch (Exception e) {
      log.error("Session dataSource create error: {}", e.getMessage(), e);
    }
  }

  public DataSource buildJdbcDataSource(String databaseName) {
    FlexmodelConfig.DatasourceConfig datasource = flexmodelConfig.datasources().get("system");
    String jdbcUrl = flexmodelConfig.projectUrlTemplate().replace("{{databaseName}}", databaseName);
    String username = datasource.username().orElse(null);
    String password = datasource.password().orElse(null);
    HikariDataSource dataSource = new HikariDataSource();
    dataSource.setMaxLifetime(30000);
    dataSource.setJdbcUrl(getContent(jdbcUrl));
    dataSource.setUsername(getContent(username));
    dataSource.setPassword(getContent(password));
    return dataSource;
  }

  @Override
  public void delete(Project project) {
    sessionFactory.unregisterSchemaProvider(project.getDatabaseName());
  }

  @Override
  @SuppressWarnings("all")
  public NativeQueryResult executeNativeQuery(Project project, String statement, Map<String, Object> parameters) {
    try (Session session = sessionFactory.createSession(project.getDatabaseName())) {
      long beginTime = System.currentTimeMillis();
      Object result = session.data().executeNativeStatement(statement, parameters);
      long endTime = System.currentTimeMillis() - beginTime;
      return new NativeQueryResult(endTime, result);
    }

  }

}
