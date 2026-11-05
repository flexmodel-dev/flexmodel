package dev.flexmodel.common;

import java.sql.SQLException;
import java.util.Objects;

/**
 * Cause-chain classification for driver-specific SQL failures.
 */
public final class SqlErrors {
  private SqlErrors() {
  }

  public static boolean constraintViolation(Throwable error) {
    for (Throwable cause = error; cause != null; cause = cause.getCause())
      if (cause instanceof SQLException sql && (Objects.toString(sql.getSQLState(), "").startsWith("23") || sql.getErrorCode() == 19))
        return true;
    return false;
  }

  public static boolean sqliteBusy(Throwable error) {
    for (Throwable cause = error; cause != null; cause = cause.getCause())
      if (cause instanceof SQLException sql && ((sql.getErrorCode() & 255) == 5 || (sql.getErrorCode() & 255) == 6))
        return true;
    return false;
  }
}
