package dev.flexmodel.sql;

import dev.flexmodel.DataSourceProvider;

import javax.sql.DataSource;

/**
 * @author cjbi
 * @deprecated Use {@link JdbcSchemaProvider} instead
 */
@Deprecated
public record JdbcDataSourceProvider(String id, DataSource dataSource) implements DataSourceProvider {
  @Override
  public String getId() {
    return id;
  }

  public JdbcSchemaProvider toSchemaProvider() {
    return new JdbcSchemaProvider(id, dataSource);
  }
}
