package dev.flexmodel.sql.type;

import dev.flexmodel.model.field.EnumRefField;
import dev.flexmodel.model.field.Field;
import dev.flexmodel.ModelRegistry;
import dev.flexmodel.type.EnumTypeHandler;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author cjbi
 */
public class EnumSqlTypeHandler extends EnumTypeHandler implements SqlTypeHandler<Object> {

  public EnumSqlTypeHandler() {
    super();
  }

  public EnumSqlTypeHandler(ModelRegistry modelRegistry, String schemaName) {
    super(modelRegistry, schemaName);
  }

  @Override
  public int getJdbcTypeCode() {
    return Types.VARCHAR;
  }

  @Override
  public Object convertParameter(Field field, Object value) {
    if (value == null) {
      return null;
    }
    Object converted = super.convertParameter(field, value);
    if (!(field instanceof EnumRefField enumField) || !enumField.isMultiple()) {
      return converted;
    }
    Collection<?> collection = converted instanceof Collection<?> col
      ? col
      : converted instanceof String[] array ? List.of(array) : List.of(converted);
    return collection.stream()
      .map(Objects::toString)
      .collect(Collectors.joining(","));
  }

  @Override
  public Object getNullableResult(ResultSet rs, String columnName, Field field) throws SQLException {
    String value = rs.getString(columnName);
    if (value == null) {
      return null;
    }
    if (!(field instanceof EnumRefField enumField)) {
      return value;
    }
    if (enumField.isMultiple()) {
      return getResult(field, List.of(value.split(",")));
    }
    return getResult(field, value);
  }
}