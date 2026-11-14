package dev.flexmodel.apps.flow.dto;

import java.util.List;
import java.util.Map;

public record AppApprovalResolveRequest(
  List<Map<String, Object>> rules,
  String initiatorId,
  Map<String, Object> variables
) {
}
