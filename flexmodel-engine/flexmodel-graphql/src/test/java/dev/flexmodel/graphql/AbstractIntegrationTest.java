package dev.flexmodel.graphql;

import dev.flexmodel.session.Session;
import dev.flexmodel.session.SessionFactory;
import dev.flexmodel.sql.JdbcSchemaProvider;
import io.agroal.api.AgroalDataSource;
import io.agroal.api.configuration.supplier.AgroalDataSourceConfigurationSupplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author cjbi
 */
public class AbstractIntegrationTest {

  protected static final Logger log = LoggerFactory.getLogger(AbstractIntegrationTest.class);
  protected static final String SCHEMA_NAME = "system";
  protected static Session session;
  protected static SessionFactory sessionFactory;

  @BeforeAll
  static void init() throws Exception {
    AgroalDataSourceConfigurationSupplier cfg = new AgroalDataSourceConfigurationSupplier();
    cfg.connectionPoolConfiguration().connectionFactoryConfiguration()
      .jdbcUrl("jdbc:sqlite:file::memory:?cache=shared");
    AgroalDataSource dataSource = AgroalDataSource.from(cfg);
    JdbcSchemaProvider jdbcSchemaProvider = new JdbcSchemaProvider("system", dataSource);
    sessionFactory = SessionFactory.builder()
      .setDefaultSchemaProvider(jdbcSchemaProvider)
      .build();
    sessionFactory.loadScript("system", "import.json");
    session = sessionFactory.createSession(SCHEMA_NAME);
  }

  @AfterAll
  static void destroy() {
    session.close();
  }

}
