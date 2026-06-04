package dev.flexmodel.projectauth.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ApiKeyResponse {
  private String id;
  private String name;
  private String keyPrefix;
  private String keyType;
  private String scopes;
  private boolean readOnly;
  private LocalDateTime expiresAt;
  private LocalDateTime lastUsedAt;
  private boolean enabled;
  private LocalDateTime createdAt;
  /**
   * 仅在创建时返回一次，其他场景为 null
   */
  private String key;

  public static ApiKeyResponse fromEntity(dev.flexmodel.codegen.entity.AuthApiKey entity) {
    ApiKeyResponse resp = new ApiKeyResponse();
    resp.setId(entity.getId());
    resp.setName(entity.getName());
    resp.setKeyPrefix(entity.getKeyPrefix());
    resp.setKeyType(entity.getKeyType());
    resp.setScopes(entity.getScopes());
    resp.setReadOnly(entity.getReadOnly());
    resp.setExpiresAt(entity.getExpiresAt());
    resp.setLastUsedAt(entity.getLastUsedAt());
    resp.setEnabled(entity.getEnabled());
    resp.setCreatedAt(entity.getCreatedAt());
    return resp;
  }
}
