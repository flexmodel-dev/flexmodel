package dev.flexmodel.sql;

import java.sql.SQLException;
import java.util.Objects;

/**
 * 数据库忙（锁等待超时）异常，典型场景为 SQLite 并发写竞争。
 * 业务方据此向客户端返回可重试提示。
 */
public class DatabaseBusyException extends SqlExecutionException {

  public DatabaseBusyException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * 沿因果链检测 SQLITE_BUSY(5) / SQLITE_LOCKED(6)。
   */
  public static boolean isBusy(Throwable error) {
    for (Throwable cause = error; cause != null; cause = cause.getCause()) {
      if (cause instanceof SQLException sql) {
        int code = sql.getErrorCode() & 255;
        String message = Objects.toString(sql.getMessage(), "");
        if (code == 5 || code == 6 || message.contains("SQLITE_BUSY") || message.contains("SQLITE_LOCKED")
          || message.contains("database is locked") || message.contains("database table is locked")) {
          return true;
        }
      }
    }
    return false;
  }
}
