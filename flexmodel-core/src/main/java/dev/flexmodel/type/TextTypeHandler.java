package dev.flexmodel.type;

import dev.flexmodel.model.field.Field;

/**
 * @author cjbi
 */
public class TextTypeHandler implements TypeHandler<String> {

  @Override
  public String convertParameter(Field field, Object value) {
    if (value == null) {
      return null;
    }
    return value.toString();
  }

}
