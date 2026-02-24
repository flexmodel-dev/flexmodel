package dev.flexmodel.sql.type;

import dev.flexmodel.model.field.Field;
import dev.flexmodel.type.StringTypeHandler;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * @author cjbi
 */
public class StringSqlTypeHandler extends StringTypeHandler implements SqlTypeHandler<String> {

  @Override
  public int getJdbcTypeCode() {
    return Types.VARCHAR;
  }

  @Override
  public String getNullableResult(ResultSet rs, String columnName, Field field) throws SQLException {
    return rs.getString(columnName);
  }
}
