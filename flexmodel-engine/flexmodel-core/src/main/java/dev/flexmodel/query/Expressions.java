package dev.flexmodel.query;

import java.util.Collections;
import java.util.Map;

/**
 * Expression factory and constants.
 *
 * @author cjbi
 */
public class Expressions {

  public static final Predicate TRUE = new Predicate(null, null, null) {
    @Override
    public Map<String, Object> toMap() {
      return Collections.emptyMap();
    }
  };

  public static <T> FilterExpression<T> field(String fieldName) {
    return new FilterExpression<>(fieldName);
  }
}
