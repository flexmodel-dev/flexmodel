package dev.flexmodel.auth.dto;

public record CreateApiKeyRequest(
  String name,
  String scope,
  String projectIds,
  boolean readOnly
) {
}
