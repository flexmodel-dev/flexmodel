package dev.flexmodel.apps.approval.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

import static dev.flexmodel.common.Values.text;

/**
 * 审批流程响应，字段与流程引擎的 flow_module 协议一致。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AppApprovalFlowResponse(
  @JsonProperty("flow_module_id") String flowModuleId,
  @JsonProperty("flow_name") String flowName,
  @JsonProperty("flow_key") String flowKey,
  @JsonProperty("flow_model") String flowModel,
  int status,
  String remark,
  Integer revision,
  Object archive,
  @JsonProperty("is_deleted") Object isDeleted,
  @JsonProperty("created_by") String createdBy,
  @JsonProperty("updated_by") String updatedBy,
  @JsonProperty("created_at") Object createdAt,
  @JsonProperty("updated_at") Object updatedAt
) {

  public static AppApprovalFlowResponse from(Map<String, Object> row) {
    return new AppApprovalFlowResponse(
      text(row, "flow_module_id"),
      text(row, "flow_name"),
      text(row, "flow_key"),
      text(row, "flow_model"),
      ((Number) row.getOrDefault("status", 1)).intValue(),
      text(row, "remark"),
      row.get("revision") instanceof Number revision ? revision.intValue() : null,
      row.get("archive"),
      row.get("is_deleted"),
      optionalText(row, "created_by"),
      optionalText(row, "updated_by"),
      row.get("created_at"),
      row.get("updated_at"));
  }

  private static String optionalText(Map<String, Object> row, String key) {
    return row.containsKey(key) ? text(row, key) : null;
  }
}
