package dev.flexmodel.notification;

import dev.flexmodel.common.ValidationException;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Plain-text interpolation only. Never evaluates expressions or scripts.
 */
public final class NotificationTemplates {
  private NotificationTemplates() {
  }

  private static final Pattern SLOT = Pattern.compile("\\{\\{\\s*([A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_-]+)*)\\s*}}");

  public static List<String> paths(String template) {
    var matcher = SLOT.matcher(template);
    List<String> paths = new ArrayList<>();
    while (matcher.find()) paths.add(matcher.group(1));
    String remaining = matcher.reset().replaceAll("");
    if (remaining.contains("{{") || remaining.contains("}}")) throw new ValidationException("通知变量格式无效");
    return paths;
  }

  public static String render(String template, Map<String, Object> variables, int max) {
    paths(template);
    String value = SLOT.matcher(template).replaceAll(match -> {
      Object current = dev.flexmodel.common.Values.path(variables, match.group(1));
      return java.util.regex.Matcher.quoteReplacement(current == null ? "" : current instanceof Map<?, ?> || current instanceof List<?> ? dev.flexmodel.JsonUtils.toJsonString(current) : current.toString());
    });
    if (value.isBlank() || value.length() > max)
      throw new ValidationException("通知内容为空或超过长度限制（" + max + "）");
    return value;
  }
}
