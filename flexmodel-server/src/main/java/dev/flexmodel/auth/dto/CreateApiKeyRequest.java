package dev.flexmodel.auth.dto;

public record CreateApiKeyRequest(
  String name,
  String projectIds,
  boolean readOnly
) {
}
