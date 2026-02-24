package dev.flexmodel.type;

import dev.flexmodel.model.field.Field;

/**
 * @author cjbi
 */
public class DecimalTypeHandler implements TypeHandler<Double> {
  @Override
  public Double convertParameter(Field field, Object value) {
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    return null;
  }
}
