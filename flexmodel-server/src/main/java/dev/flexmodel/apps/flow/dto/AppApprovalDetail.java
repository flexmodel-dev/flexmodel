package dev.flexmodel.apps.flow.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

import static dev.flexmodel.common.Values.objects;
import static dev.flexmodel.common.Values.text;

/**
 * 审批实例详情。快照、变量、日志等仍保留引擎的动态结构。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AppApprovalDetail(
  String id,
  String appId,
  String appName,
  String title,
  String formName,
  String recordId,
  String initiator,
  String status,
  Object createdAt,
  boolean canManage,
  Map<String, Object> snapshot,
  Map<String, Object> variables,
  String error,
  List<AppApprovalTaskResponse> tasks,
  List<Map<String, Object>> waits,
  List<Map<String, Object>> upcoming,
  Map<String, Object> ending,
  List<Map<String, Object>> logs,
  List<AppApprovalItem> children,
  List<Map<String, Object>> notificationDelivery,
  List<Map<String, Object>> delivery
) {

  public static AppApprovalDetail from(Map<String, Object> row) {
    return new AppApprovalDetail(
      text(row, "id"),
      text(row, "appId"),
      text(row, "appName"),
      text(row, "title"),
      text(row, "formName"),
      text(row, "recordId"),
      text(row, "initiator"),
      text(row, "status"),
      row.get("createdAt"),
      Boolean.TRUE.equals(row.get("canManage")),
      map(row.get("snapshot")),
      map(row.get("variables")),
      text(row, "error"),
      objects(row.get("tasks")).stream().map(AppApprovalTaskResponse::from).toList(),
      objects(row.get("waits")).stream().map(AppApprovalDetail::presentation).toList(),
      objects(row.get("upcoming")).stream().map(AppApprovalDetail::presentation).toList(),
      presentation(map(row.get("ending"))),
      objects(row.get("logs")).stream().map(AppApprovalDetail::presentation).toList(),
      objects(row.get("children")).stream().map(AppApprovalItem::from).toList(),
      objects(row.get("notificationDelivery")),
      objects(row.get("delivery")));
  }

  private static Map<String, Object> presentation(Map<String, Object> row) {
    if (row == null) return null;
    var result = new java.util.LinkedHashMap<>(row);
    for (var entry : Map.of("sign_mode", "signMode", "node_key", "nodeKey", "created_at", "createdAt", "run_id", "runId").entrySet())
      if (result.containsKey(entry.getKey())) result.put(entry.getValue(), result.remove(entry.getKey()));
    if (result.containsKey("candidates"))
      result.put("candidates", objects(result.get("candidates")).stream().map(AppApprovalDetail::presentation).toList());
    return result;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> map(Object value) {
    return value instanceof Map<?, ?> ? (Map<String, Object>) value : null;
  }
}
