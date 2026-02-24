package dev.flexmodel.type;

import dev.flexmodel.model.field.Field;

/**
 * @author cjbi
 */
public class BooleanTypeHandler implements TypeHandler<Boolean> {

  @Override
  public Boolean convertParameter(Field field, Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Boolean bool) {
      return bool;
    }
    return Boolean.valueOf(value.toString());
  }

}
