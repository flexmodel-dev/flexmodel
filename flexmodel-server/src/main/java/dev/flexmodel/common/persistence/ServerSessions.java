package dev.flexmodel.common.persistence;

import dev.flexmodel.apps.approval.AppApprovalRecordGuard;
import dev.flexmodel.common.ProjectUnitOfWork;
import dev.flexmodel.session.Session;
import dev.flexmodel.session.SessionFactory;
import dev.flexmodel.sql.JdbcSchemaProvider;
import dev.flexmodel.sql.NamedParameterSqlExecutor;
import dev.flexmodel.sql.SqlContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Application data sessions. Standalone engine clients retain the original factory behavior.
 */
@ApplicationScoped
public class ServerSessions {
  @Inject
  SessionFactory factory;
  @Inject
  AppApprovalRecordGuard guard;

  public Session open(String schema) {
    Session borrowed = ProjectUnitOfWork.borrowSchema(schema);
    if (borrowed != null) return borrowed;
    return factory.getSchemaProvider(schema) instanceof JdbcSchemaProvider ? openOwned(schema) : factory.createSession(schema);
  }

  public ManagedSession openOwned(String schema) {
    return openOwned(schema, false);
  }

  public ManagedSession openSnapshot(String schema) {
    return openOwned(schema, true);
  }

  private ManagedSession openOwned(String schema, boolean snapshot) {
    if (!(factory.getSchemaProvider(schema) instanceof JdbcSchemaProvider provider))
      throw new UnsupportedOperationException("服务端事务数据访问需要 SQL 数据源");
    Connection connection = null;
    try {
      connection = provider.dataSource().getConnection();
      if (snapshot) {
        if (!connection.getMetaData().supportsTransactionIsolationLevel(Connection.TRANSACTION_SERIALIZABLE))
          throw new UnsupportedOperationException("数据源不支持一致性导出所需的串行化事务");
        connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
      }
      if ("SQLite".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName())) {
        // Development-only contention tolerance; never retry business operations implicitly.
        try (var statement = connection.createStatement()) {
          statement.execute("PRAGMA busy_timeout=5000");
        }
      } else if (!snapshot) connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
      SqlContext context = new SqlContext(schema, new NamedParameterSqlExecutor(connection), factory.getModelRegistry(), factory);
      return new ServerSqlSession(context, guard);
    } catch (RuntimeException | SQLException error) {
      if (connection != null) try {
        connection.close();
      } catch (SQLException closeError) {
        error.addSuppressed(closeError);
      }
      throw new IllegalStateException("无法创建服务端数据会话: " + schema, error);
    }
  }

  public static ManagedSession managed(Session session) {
    if (session instanceof ManagedSession managed) return managed;
    throw new IllegalArgumentException("服务端事务必须使用 ServerSessions 创建的会话");
  }
}
