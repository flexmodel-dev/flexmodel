package dev.flexmodel.quarkus.config;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import dev.flexmodel.session.SessionFactory;
import dev.flexmodel.session.SessionManager;
import dev.flexmodel.sql.JdbcSchemaProvider;

/**
 * Session配置类
 * 配置SessionFactory和SessionManager
 *
 * @author cjbi
 */
@ApplicationScoped
public class SessionConfig {

  @Produces
  @ApplicationScoped
  public SessionFactory sessionFactory() {
    HikariDataSource dataSource = new HikariDataSource();
    dataSource.setMaxLifetime(30000);
    dataSource.setJdbcUrl("jdbc:sqlite:file::memory:?cache=shared");

    return SessionFactory.builder()
      .setDefaultSchemaProvider(new JdbcSchemaProvider("system", dataSource))
      .build();
  }

  @Produces
  @ApplicationScoped
  public SessionManager sessionManager(SessionFactory sessionFactory) {
    return new SessionManager(sessionFactory);
  }
}
