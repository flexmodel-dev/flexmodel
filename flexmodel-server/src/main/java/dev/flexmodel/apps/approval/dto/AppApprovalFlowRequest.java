package dev.flexmodel.apps.approval.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 审批流程保存请求，字段与流程引擎的 flow_module 协议一致。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AppApprovalFlowRequest(
  @JsonProperty("flow_module_id") String flowModuleId,
  @JsonProperty("flow_name") String flowName,
  @JsonProperty("flow_key") String flowKey,
  @JsonProperty("flow_model") String flowModel,
  Integer status,
  String remark,
  Integer revision,
  Object archive,
  @JsonProperty("is_deleted") Object isDeleted,
  @JsonProperty("created_by") String createdBy,
  @JsonProperty("updated_by") String updatedBy,
  @JsonProperty("created_at") Object createdAt,
  @JsonProperty("updated_at") Object updatedAt
) {

  public Map<String, Object> toMap() {
    Map<String, Object> value = new LinkedHashMap<>();
    put(value, "flow_module_id", flowModuleId);
    put(value, "flow_name", flowName);
    put(value, "flow_key", flowKey);
    put(value, "flow_model", flowModel);
    put(value, "status", status);
    put(value, "remark", remark);
    put(value, "revision", revision);
    put(value, "archive", archive);
    put(value, "is_deleted", isDeleted);
    put(value, "created_by", createdBy);
    put(value, "updated_by", updatedBy);
    put(value, "created_at", createdAt);
    put(value, "updated_at", updatedAt);
    return value;
  }

  private static void put(Map<String, Object> value, String key, Object item) {
    if (item != null) {
      value.put(key, item);
    }
  }
}
