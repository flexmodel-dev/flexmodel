package dev.flexmodel.sql;

import dev.flexmodel.SchemaProvider;

import javax.sql.DataSource;

/**
 * JDBC Schema Provider
 * Provides JDBC-based schema implementation
 *
 * @author cjbi
 */
public record JdbcSchemaProvider(String id, DataSource dataSource) implements SchemaProvider {
  @Override
  public String getName() {
    return id;
  }
}
