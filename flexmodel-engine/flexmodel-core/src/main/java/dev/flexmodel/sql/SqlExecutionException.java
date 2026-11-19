package dev.flexmodel.sql;

import java.sql.SQLException;

/**
 * @author cjbi
 */
public class SqlExecutionException extends RuntimeException {


  public SqlExecutionException(String message) {
    super(message);
  }

  public SqlExecutionException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * 按因果链分类：忙等待返回 DatabaseBusyException，其余返回通用执行异常。
   */
  public static SqlExecutionException of(String message, SQLException cause) {
    if (DatabaseBusyException.isBusy(cause)) {
      return new DatabaseBusyException(message, cause);
    }
    return new SqlExecutionException(message, cause);
  }

}
