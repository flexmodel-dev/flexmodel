package dev.flexmodel.auth.dto;

public record CreateApiKeyRequest(
  String name,
  String keyType,
  String scopes,
  String projectIds,
  boolean readOnly
) {
}
