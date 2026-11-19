package dev.flexmodel.apps.flow.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

import static dev.flexmodel.common.Values.text;

/**
 * 审批列表项：待办、已办与我发起的实例共用。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AppApprovalItem(
  String id,
  String appId,
  String appName,
  String title,
  String formName,
  String recordId,
  String initiator,
  String status,
  Object createdAt,
  String taskId,
  String nodeTitle,
  String taskStatus,
  String decision,
  String triggerType,
  String principal
) {

  public static AppApprovalItem from(Map<String, Object> row) {
    return new AppApprovalItem(
      text(row, "id"),
      text(row, "appId"),
      text(row, "appName"),
      text(row, "title"),
      text(row, "formName"),
      text(row, "recordId"),
      text(row, "initiator"),
      text(row, "status"),
      row.get("createdAt"),
      optional(row, "taskId"),
      optional(row, "nodeTitle"),
      optional(row, "taskStatus"),
      optional(row, "decision"), text(row, "triggerType"), text(row, "principal"));
  }

  private static String optional(Map<String, Object> row, String key) {
    return row.containsKey(key) ? text(row, key) : null;
  }
}
