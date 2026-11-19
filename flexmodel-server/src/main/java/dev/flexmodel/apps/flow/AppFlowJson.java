package dev.flexmodel.apps.flow;

import dev.flexmodel.JsonUtils;

import java.util.*;

import static dev.flexmodel.common.Values.*;

public final class AppFlowJson {
  private AppFlowJson() {
  }

  public static Map<String, Object> config(Map<String, Object> node) {
    return object(object(node.get("data")).get("config"));
  }

  public static Object value(Map<String, Object> binding, Map<String, Object> variables) {
    if ("variable".equals(binding.get("kind"))) return path(variables, text(binding, "value"));
    String value = text(binding, "value");
    return switch (text(binding, "valueType")) {
      case "number" -> new java.math.BigDecimal(value);
      case "boolean" -> Boolean.parseBoolean(value);
      default -> value;
    };
  }

  public static Map<String, Object> copy(Map<String, Object> value) {
    return JsonUtils.parseToMap(JsonUtils.toJsonString(value));
  }

  public static List<Map<String, Object>> flatten(List<Map<String, Object>> nodes) {
    List<Map<String, Object>> result = new ArrayList<>();
    for (var node : nodes) {
      result.add(node);
      result.addAll(flatten(objects(node.get("blocks"))));
    }
    return result;
  }
}
