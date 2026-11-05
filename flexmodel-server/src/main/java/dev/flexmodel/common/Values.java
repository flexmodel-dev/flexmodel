package dev.flexmodel.common;

import java.util.*;

/**
 * Shared navigation helpers over JSON-shaped maps and lists.
 */
public final class Values {
  private Values() {
  }

  @SuppressWarnings("unchecked")
  public static Map<String, Object> object(Object value) {
    return value instanceof Map<?, ?> ? (Map<String, Object>) value : new LinkedHashMap<>();
  }

  public static List<Map<String, Object>> objects(Object value) {
    return value instanceof List<?> list ? list.stream().map(Values::object).toList() : List.of();
  }

  public static List<String> strings(Object value) {
    return value instanceof Collection<?> list ? list.stream().map(String::valueOf).toList()
      : value instanceof String string && !string.isBlank() ? List.of(string) : List.of();
  }

  public static String text(Map<String, Object> value, String key) {
    return Objects.toString(value.get(key), "");
  }

  public static Object path(Map<String, Object> data, String path) {
    Object current = data;
    for (String part : path.split("\\.")) {
      if (current instanceof Map<?, ?> map) current = map.get(part);
      else if (current instanceof List<?> list && part.length() < 9 && part.matches("\\d+") && Integer.parseInt(part) < list.size())
        current = list.get(Integer.parseInt(part));
      else return null;
    }
    return current;
  }
}
