package dev.flexmodel.apps.approval.dto;

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
  List<Map<String, Object>> tasks,
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
      objects(row.get("tasks")),
      objects(row.get("waits")),
      objects(row.get("upcoming")),
      map(row.get("ending")),
      objects(row.get("logs")),
      objects(row.get("children")).stream().map(AppApprovalItem::from).toList(),
      objects(row.get("notificationDelivery")),
      objects(row.get("delivery")));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> map(Object value) {
    return value instanceof Map<?, ?> ? (Map<String, Object>) value : null;
  }
}
