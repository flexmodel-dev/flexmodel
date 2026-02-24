package dev.flexmodel.sql.type;

import dev.flexmodel.model.field.Field;
import dev.flexmodel.type.UnknownTypeHandler;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * @author cjbi
 */
public class UnknownSqlTypeHandler extends UnknownTypeHandler implements SqlTypeHandler<Object> {

  @Override
  public int getJdbcTypeCode() {
    return Types.VARCHAR;
  }

  @Override
  public Object getNullableResult(ResultSet rs, String columnName, Field field) throws SQLException {
    return rs.getObject(columnName);
  }
}
