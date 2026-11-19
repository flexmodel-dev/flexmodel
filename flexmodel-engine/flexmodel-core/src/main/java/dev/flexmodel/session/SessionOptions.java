package dev.flexmodel.session;

import java.sql.Connection;

/**
 * 会话创建选项。
 */
public record SessionOptions(int isolation, boolean applyIsolation, boolean skipWriteInterceptors) {

  public static final SessionOptions NONE = new SessionOptions(-1, false, false);

  public static SessionOptions none() {
    return NONE;
  }

  public static SessionOptions readCommitted() {
    return new SessionOptions(Connection.TRANSACTION_READ_COMMITTED, true, false);
  }

  public static SessionOptions serializable() {
    return new SessionOptions(Connection.TRANSACTION_SERIALIZABLE, true, false);
  }

  /**
   * schema 导入会话：跳过业务写拦截器，避免引导期写触发拦截器内的新会话/Bean 重入。
   */
  public static SessionOptions schemaImport() {
    return new SessionOptions(-1, false, true);
  }
}
