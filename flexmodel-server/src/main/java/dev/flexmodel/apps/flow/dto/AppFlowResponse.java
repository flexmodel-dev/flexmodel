package dev.flexmodel.apps.flow.dto;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.flow.dto.model.FlowModel;

import java.util.Map;

import static dev.flexmodel.common.Values.*;

public record AppFlowResponse(String id, String name, String flowKey, String description, String status,
                              FlowModel model, Object updatedAt, Map<String, Object> publishedTrigger,
                              String deploymentId, boolean hasDraftChanges) {
  public static AppFlowResponse from(Map<String, Object> row) {
    int state = ((Number) row.getOrDefault("status", 1)).intValue();
    return new AppFlowResponse(text(row, "flow_module_id"), text(row, "flow_name"), text(row, "flow_key"), text(row, "remark"),
      state == 2 ? "enabled" : state == 3 ? "disabled" : "draft",
      JsonUtils.parseToObject(text(row, "flow_model"), FlowModel.class), row.get("updated_at"), object(row.get("publishedTrigger")), text(row, "deploymentId"), Boolean.TRUE.equals(row.get("hasDraftChanges")));
  }
}
