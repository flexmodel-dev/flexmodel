package dev.flexmodel.apps.flow.dto;

import java.util.List;
import java.util.Map;

import static dev.flexmodel.common.Values.objects;

public record AppApprovalDirectory(
  List<Map<String, Object>> roles,
  List<Map<String, Object>> members,
  List<Map<String, Object>> organizations
) {

  public static AppApprovalDirectory from(Map<String, Object> row) {
    return new AppApprovalDirectory(
      objects(row.get("roles")),
      objects(row.get("members")),
      objects(row.get("organizations")));
  }
}
