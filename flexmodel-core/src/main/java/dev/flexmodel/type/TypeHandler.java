package dev.flexmodel.type;

import dev.flexmodel.model.field.Field;

/**
 * @author cjbi
 */
public interface TypeHandler<T> {

  T convertParameter(Field field, Object value);

}
