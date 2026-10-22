package dev.flexmodel.approval;

import dev.flexmodel.JsonUtils;

import java.util.*;

public final class ApprovalJson {
  private ApprovalJson() {
  }

  @SuppressWarnings("unchecked")
  public static Map<String, Object> object(Object value) {
    return value instanceof Map<?, ?> ? (Map<String, Object>) value : new LinkedHashMap<>();
  }

  @SuppressWarnings("unchecked")
  public static List<Map<String, Object>> objects(Object value) {
    return value instanceof List<?> list ? list.stream().map(ApprovalJson::object).toList() : List.of();
  }

  public static List<String> strings(Object value) {
    return value instanceof Collection<?> list ? list.stream().map(String::valueOf).toList()
      : value instanceof String string && !string.isBlank() ? List.of(string) : List.of();
  }

  public static String text(Map<String, Object> value, String key) {
    return Objects.toString(value.get(key), "");
  }

  public static Map<String, Object> config(Map<String, Object> node) {
    return object(object(node.get("data")).get("config"));
  }

  public static Object path(Map<String, Object> data, String path) {
    Object current = data;
    for (String part : path.split("\\.")) {
      if (current instanceof Map<?, ?> map) current = map.get(part);
      else if (current instanceof List<?> list && part.matches("\\d+") && Integer.parseInt(part) < list.size())
        current = list.get(Integer.parseInt(part));
      else return null;
    }
    return current;
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
