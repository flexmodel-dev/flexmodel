package dev.flexmodel.apps.ai;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.apps.ai.dto.AiDtos.AppChange;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * 从已完成的共享工具调用提取变更范围，不向浏览器泄露工具参数或业务数据。
 */
final class AiToolChanges {
  private static final Map<String, String> RESOURCES = Map.ofEntries(
    Map.entry("create_app", "app"),
    Map.entry("create_app_form", "form"),
    Map.entry("save_app_form_layout", "form"),
    Map.entry("save_app_form_views", "form"),
    Map.entry("create_app_workbench", "workbench"),
    Map.entry("save_app_workbench_layout", "workbench"),
    Map.entry("create_app_navigation", "navigation"),
    Map.entry("create_app_workbench_navigation", "navigation"),
    Map.entry("create_app_mock_records", "record"),
    Map.entry("save_app_approval_flow", "approval"),
    Map.entry("set_app_approval_flow_status", "approval")
  );
  private static final Pattern PARTIAL_RECORDS = Pattern.compile(
    "^Error: create_app_mock_records failed at record index ([0-9]+) - ");

  private AiToolChanges() {
  }

  static AppChange from(String tool, String arguments, String result, boolean failed) {
    String resource = RESOURCES.get(tool);
    if (resource == null || result == null || failed) return null;
    try {
      boolean error = result.stripLeading().startsWith("Error:");
      // 示例记录逐条提交：第 N 条失败时，前 N-1 条仍已成功，需要刷新。
      var partial = PARTIAL_RECORDS.matcher(result);
      if (error && !("record".equals(resource) && partial.find()
        && Long.parseLong(partial.group(1)) > 1)) return null;
      Map<String, Object> args = JsonUtils.parseToMap(arguments);
      Map<String, Object> output = !error && result.stripLeading().startsWith("{")
        ? JsonUtils.parseToMap(result) : Map.of();
      String appId = "create_app".equals(tool) ? text(output, "id") : text(args, "appId");
      if (appId == null) return null;
      String formName = "form".equals(resource) || "record".equals(resource) ? text(args, "formName") : null;
      if (formName == null && "form".equals(resource)) formName = text(output, "name");
      return new AppChange(appId, resource, formName, text(output, "id"));
    } catch (RuntimeException ignored) {
      // 变更提示不能中断已经成功的工具调用或 AI 回复。
      return null;
    }
  }

  private static String text(Map<String, Object> values, String key) {
    return values != null && values.get(key) instanceof String value && !value.isBlank() ? value : null;
  }
}
