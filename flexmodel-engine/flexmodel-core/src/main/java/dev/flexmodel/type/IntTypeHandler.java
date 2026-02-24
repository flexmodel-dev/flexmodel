package dev.flexmodel.type;

import dev.flexmodel.model.field.Field;

/**
 * @author cjbi
 */
public class IntTypeHandler implements TypeHandler<Integer> {
  @Override
  public Integer convertParameter(Field field, Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Number number) {
      return number.intValue();
    }
    return Integer.valueOf(value.toString());
  }
}
