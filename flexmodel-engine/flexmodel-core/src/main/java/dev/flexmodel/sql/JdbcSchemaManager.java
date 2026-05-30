package dev.flexmodel.sql;

import dev.flexmodel.sql.dialect.SqlDialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 基于 JDBC 的 SchemaManager 实现，通过 SqlDialect 方言体系适配不同数据库。
 *
 * @author cjbi
 */
public class JdbcSchemaManager implements SchemaManager {

  private static final Logger log = LoggerFactory.getLogger(JdbcSchemaManager.class);

  @Override
  public void createSchema(DataSource systemDataSource, String schemaName) {
    try (Connection connection = systemDataSource.getConnection()) {
      SqlDialect dialect = SqlDialectFactory.create(connection.getMetaData());

      if (!dialect.supportsAutoCreateSchema()) {
        throw new UnsupportedOperationException(
          "数据库类型 " + connection.getMetaData().getDatabaseProductName() +
            " 不支持自动创建 Schema，请由 DBA 预先创建 Schema/用户: " + schemaName);
      }

      String[] sqls = dialect.getCreateSchemaIfNotExistsSql(schemaName);
      try (Statement statement = connection.createStatement()) {
        for (String sql : sqls) {
          if (sql != null && !sql.isBlank()) {
            log.info("Executing create schema SQL: {}", sql);
            statement.execute(sql);
          }
        }
      }
      log.info("Schema '{}' created successfully (or already exists)", schemaName);
    } catch (UnsupportedOperationException e) {
      throw e;
    } catch (SQLException e) {
      throw new RuntimeException("Failed to create schema '" + schemaName + "': " + e.getMessage(), e);
    }
  }

  @Override
  public void dropSchema(DataSource systemDataSource, String schemaName) {
    try (Connection connection = systemDataSource.getConnection()) {
      SqlDialect dialect = SqlDialectFactory.create(connection.getMetaData());

      String[] sqls = dialect.getDropSchemaIfExistsSql(schemaName);
      try (Statement statement = connection.createStatement()) {
        for (String sql : sqls) {
          if (sql != null && !sql.isBlank()) {
            log.info("Executing drop schema SQL: {}", sql);
            statement.execute(sql);
          }
        }
      }
      log.info("Schema '{}' dropped successfully (or did not exist)", schemaName);
    } catch (SQLException e) {
      throw new RuntimeException("Failed to drop schema '" + schemaName + "': " + e.getMessage(), e);
    }
  }

  @Override
  public boolean schemaExists(DataSource systemDataSource, String schemaName) {
    try (Connection connection = systemDataSource.getConnection()) {
      // 优先通过 DatabaseMetaData.getSchemas() 判断
      try (ResultSet rs = connection.getMetaData().getSchemas()) {
        while (rs.next()) {
          String existingSchema = rs.getString("TABLE_SCHEM");
          if (schemaName.equalsIgnoreCase(existingSchema)) {
            return true;
          }
        }
      }
      // 部分数据库（如 MySQL）getSchemas 返回空，尝试通过 getCatalogs 判断
      try (ResultSet rs = connection.getMetaData().getCatalogs()) {
        while (rs.next()) {
          String existingCatalog = rs.getString("TABLE_CAT");
          if (schemaName.equalsIgnoreCase(existingCatalog)) {
            return true;
          }
        }
      }
      return false;
    } catch (SQLException e) {
      log.warn("Failed to check schema existence for '{}': {}", schemaName, e.getMessage());
      return false;
    }
  }
}
