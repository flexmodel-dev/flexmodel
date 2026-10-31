package dev.flexmodel.apps.approval.dto;

import java.util.Map;

public record AppApprovalIssue(String nodeId, String message) {

  public static AppApprovalIssue from(Map<String, String> row) {
    return new AppApprovalIssue(row.get("nodeId"), row.get("message"));
  }
}
