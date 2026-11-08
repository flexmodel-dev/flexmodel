package dev.flexmodel.common.persistence;

import dev.flexmodel.common.ProjectUnitOfWork;
import dev.flexmodel.session.Session;
import dev.flexmodel.session.SessionFactory;
import dev.flexmodel.session.SessionOptions;
import dev.flexmodel.sql.JdbcSchemaProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Application data sessions. Standalone engine clients retain the original factory behavior.
 */
@ApplicationScoped
public class ServerSessions {
  @Inject
  SessionFactory factory;

  /**
   * 事务内借出当前会话，否则按服务端策略新建（READ_COMMITTED + 方言连接准备 + 写拦截器）。
   */
  public Session open(String schema) {
    Session borrowed = ProjectUnitOfWork.borrowSchema(schema);
    if (borrowed != null) return borrowed;
    return factory.getSchemaProvider(schema) instanceof JdbcSchemaProvider
      ? factory.createSession(schema, SessionOptions.readCommitted())
      : factory.createSession(schema);
  }

  /**
   * SERIALIZABLE 一致性导出快照会话；数据源不支持串行化时抛出。
   */
  public Session openSnapshot(String schema) {
    return factory.createSession(schema, SessionOptions.serializable());
  }
}
