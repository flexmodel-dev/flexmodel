package dev.flexmodel.codegen;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import dev.flexmodel.session.Session;
import dev.flexmodel.session.SessionFactory;
import dev.flexmodel.sql.JdbcSchemaProvider;

/**
 * @author cjbi
 */
public class AbstractIntegrationTest {

  protected static final Logger log = LoggerFactory.getLogger(AbstractIntegrationTest.class);
  protected static final String SCHEMA_NAME = "system";
  protected static Session session;

  @BeforeAll
  static void init() {
    HikariDataSource dataSource = new HikariDataSource();
    dataSource.setJdbcUrl("jdbc:sqlite:file::memory:?cache=shared");
    JdbcSchemaProvider jdbcSchemaProvider = new JdbcSchemaProvider(SCHEMA_NAME, dataSource);
    SessionFactory sessionFactory = SessionFactory.builder()
      .setDefaultSchemaProvider(jdbcSchemaProvider)
      .build();
    session = sessionFactory.createSession(SCHEMA_NAME);
  }

  @AfterAll
  static void destroy() {
    session.close();
  }

}
