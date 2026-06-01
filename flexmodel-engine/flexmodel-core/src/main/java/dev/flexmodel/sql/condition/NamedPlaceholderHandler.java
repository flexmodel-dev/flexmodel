package dev.flexmodel.sql.condition;

import java.util.HashMap;
import java.util.Map;

/**
 * 以命名参数方式渲染占位符。
 */
public class NamedPlaceholderHandler implements PlaceholderHandler {

  private final Map<String, Object> parameters = new HashMap<>();
  private int placeholderIndex;

  @Override
  public String handle(String key, Object value) {
    // 将列表达式清理为合法的命名参数名：只保留字母、数字和下划线
    String sanitized = key.replaceAll("[^a-zA-Z0-9_]", "_")
      .replaceAll("_+", "_");
    // 去除首尾下划线
    sanitized = sanitized.replaceAll("^_+|_+$", "");
    String name = sanitized + "_" + placeholderIndex++;
    parameters.put(name, value);
    return ":" + name;
  }

  @Override
  public Map<String, Object> getParameters() {
    return parameters;
  }
}

