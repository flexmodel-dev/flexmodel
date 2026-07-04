package dev.flexmodel;

import dev.flexmodel.sql.JdbcSchemaProvider;
import io.agroal.api.AgroalDataSource;
import io.agroal.api.configuration.supplier.AgroalConnectionFactoryConfigurationSupplier;
import io.agroal.api.configuration.supplier.AgroalDataSourceConfigurationSupplier;
import io.agroal.api.security.NamePrincipal;
import io.agroal.api.security.SimplePassword;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * @author cjbi
 */
@Testcontainers
public class DmIntegrationTests extends AbstractSessionTests {

  @Container
  public static DmContainer container = new DmContainer<>();

  @BeforeAll
  public static void beforeAll() throws Exception {
    AgroalDataSourceConfigurationSupplier cfg = new AgroalDataSourceConfigurationSupplier();
    AgroalConnectionFactoryConfigurationSupplier factoryCfg = cfg.connectionPoolConfiguration().connectionFactoryConfiguration();
    factoryCfg.jdbcUrl(container.getJdbcUrl());
    factoryCfg.principal(new NamePrincipal(container.getUsername()));
    factoryCfg.credential(new SimplePassword(container.getPassword()));
    AgroalDataSource dataSource = AgroalDataSource.from(cfg);
    initSession(new JdbcSchemaProvider("default", dataSource));
  }
}
