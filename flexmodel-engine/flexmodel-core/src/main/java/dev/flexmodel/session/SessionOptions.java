package dev.flexmodel.session;

import java.sql.Connection;

/**
 * 会话创建选项。
 */
public record SessionOptions(int isolation, boolean applyIsolation) {

  public static final SessionOptions NONE = new SessionOptions(-1, false);

  public static SessionOptions none() {
    return NONE;
  }

  public static SessionOptions readCommitted() {
    return new SessionOptions(Connection.TRANSACTION_READ_COMMITTED, true);
  }

  public static SessionOptions serializable() {
    return new SessionOptions(Connection.TRANSACTION_SERIALIZABLE, true);
  }
}
