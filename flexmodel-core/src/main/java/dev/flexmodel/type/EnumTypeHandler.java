package dev.flexmodel.type;

import dev.flexmodel.model.field.Field;

/**
 * @author cjbi
 */
public class EnumTypeHandler implements TypeHandler<Object> {
  @Override
  public Object convertParameter(Field field, Object value) {
    return value;
  }
}
