package dev.flexmodel.projectauth.dto;

public record CreateApiKeyRequest(
  String name,
  String keyType,
  String scopes,
  boolean readOnly
) {
}
