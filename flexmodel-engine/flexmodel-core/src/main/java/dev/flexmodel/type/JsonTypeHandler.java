package dev.flexmodel.type;

import dev.flexmodel.model.field.Field;

/**
 * @author cjbi
 */
public class JsonTypeHandler implements TypeHandler<Object> {

    public JsonTypeHandler() {
    }

    @Override
    public Object convertParameter(Field field, Object value) {
    return value;
  }
}
