package dev.flexmodel.apps.approval.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

import static dev.flexmodel.common.Values.text;

/**
 * 签核结果，字段与个人待办表 f_app_approval_task 一致。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AppApprovalTaskResponse(
  String id,
  @JsonProperty("run_id") String runId,
  @JsonProperty("wait_id") String waitId,
  @JsonProperty("user_id") String userId,
  Integer position,
  String status,
  String decision,
  String comment,
  @JsonProperty("request_key") String requestKey,
  @JsonProperty("created_at") Object createdAt,
  @JsonProperty("decided_at") Object decidedAt
) {

  public static AppApprovalTaskResponse from(Map<String, Object> row) {
    return new AppApprovalTaskResponse(
      text(row, "id"),
      optionalText(row, "run_id"),
      optionalText(row, "wait_id"),
      optionalText(row, "user_id"),
      row.get("position") instanceof Number position ? position.intValue() : null,
      optionalText(row, "status"),
      optionalText(row, "decision"),
      optionalText(row, "comment"),
      optionalText(row, "request_key"),
      row.get("created_at"),
      row.get("decided_at"));
  }

  private static String optionalText(Map<String, Object> row, String key) {
    return row.containsKey(key) ? text(row, key) : null;
  }
}
