package dev.flexmodel.sql.type;

import dev.flexmodel.model.field.Field;
import dev.flexmodel.type.TextTypeHandler;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * @author cjbi
 */
public class TextSqlTypeHandler extends TextTypeHandler implements SqlTypeHandler<String> {

  @Override
  public int getJdbcTypeCode() {
    return Types.LONGVARCHAR;
  }

  @Override
  public String getNullableResult(ResultSet rs, String columnName, Field field) throws SQLException {
    return rs.getString(columnName);
  }
}
