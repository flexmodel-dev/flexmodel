package dev.flexmodel.sql.type;

import dev.flexmodel.model.field.Field;
import dev.flexmodel.type.TypeHandler;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @author cjbi
 */
public interface SqlTypeHandler<T> extends TypeHandler<T> {

  int getJdbcTypeCode();

  default T getResult(ResultSet rs, String columnName, Field field) throws SQLException {
    try {
      return getNullableResult(rs, columnName, field);
    } catch (Exception e) {
      throw new ResultMapException("Error attempting to get column '" + columnName + "' from result set.  Cause: " + e, e);
    }
  }

  T getNullableResult(ResultSet rs, String columnName, Field field) throws SQLException;

}
