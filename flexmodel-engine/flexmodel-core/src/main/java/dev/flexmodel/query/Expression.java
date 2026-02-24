package dev.flexmodel.query;

import dev.flexmodel.JsonUtils;

import java.util.Map;

/**
 * @author cjbi
 */
public interface Expression {

  Map<String, Object> toMap();

  default String toJsonString() {
    Map<String, Object> map = toMap();
    if (map == null || map.isEmpty()) {
      return null;
    }
    return JsonUtils.toJsonString(toMap());
  }

}
