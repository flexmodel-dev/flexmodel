package dev.flexmodel.apps.flow.dto;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.flow.dto.model.FlowModel;

import java.util.*;

/**
 * Public authoring contract; engine storage names remain internal.
 */
public record AppFlowRequest(String id, String name, String flowKey, String description,
                             FlowModel model) {
  public Map<String, Object> toMap() {
    var v = new LinkedHashMap<String, Object>();
    v.put("flow_module_id", Objects.toString(id, ""));
    v.put("flow_name", Objects.toString(name, ""));
    v.put("flow_key", Objects.toString(flowKey, ""));
    v.put("remark", Objects.toString(description, ""));
    v.put("flow_model", JsonUtils.toJsonString(model));
    return v;
  }
}
