package dev.flexmodel.sql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import dev.flexmodel.SchemaProvider;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author cjbi
 */
public class SqlConnectionHolder {

  Logger log = LoggerFactory.getLogger(SqlConnectionHolder.class);

  private final Map<String, SchemaProvider> schemaProviderMap;
  private final Map<String, Connection> connections = new HashMap<>();

  public SqlConnectionHolder(Map<String, SchemaProvider> schemaProviderMap) {
    this.schemaProviderMap = schemaProviderMap;
  }

  public Connection getOrCreateConnection(String identifier) {
    return connections.compute(identifier, (k, v) -> {
      try {
        if (v != null && !v.isClosed()) {
          try {
            return v;
          } catch (Exception e) {
            DataSource dataSource = ((JdbcSchemaProvider) schemaProviderMap.get(identifier)).dataSource();
            return dataSource.getConnection();
          }
        }
        DataSource dataSource = ((JdbcSchemaProvider) schemaProviderMap.get(identifier)).dataSource();
        return dataSource.getConnection();
      } catch (SQLException e) {
        throw new RuntimeException(e);
      }
    });
  }


  public void destroy() {
    for (Map.Entry<String, Connection> entry : connections.entrySet()) {
      closeConnection(entry.getValue());
    }
    connections.clear();
  }

  private void closeConnection(Connection conn) {
    if (conn != null) {
      try {
        conn.close();
      } catch (SQLException ex) {
        log.debug("Could not close JDBC Connection", ex);
      } catch (Throwable ex) {
        log.debug("Unexpected exception on closing JDBC Connection", ex);
      }
    }
  }

}
