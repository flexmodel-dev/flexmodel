package dev.flexmodel.auth.service;

import dev.flexmodel.auth.repository.ApiKeyRepository;
import dev.flexmodel.codegen.entity.AuthApiKey;
import dev.flexmodel.auth.dto.ApiKeyResponse;
import dev.flexmodel.auth.dto.CreateApiKeyRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@ApplicationScoped
public class ApiKeyService {

  @Inject
  ApiKeyRepository apiKeyRepository;

  public List<ApiKeyResponse> listAll() {
    return apiKeyRepository.findAll().stream()
      .map(ApiKeyResponse::fromEntity)
      .toList();
  }

  /**
   * 创建 API Key，返回包含明文 key 的响应（仅此一次）。
   */
  public ApiKeyResponse create(CreateApiKeyRequest request) {
    String keyType = request.keyType() != null ? request.keyType() : "custom";
    ApiKeyGenerator.GeneratedKey generated = ApiKeyGenerator.generate(keyType);

    AuthApiKey entity = new AuthApiKey();
    entity.setName(request.name());
    entity.setKeyHash(generated.hash());
    entity.setKeyPrefix(generated.prefix());
    entity.setKeyType(keyType);
    entity.setScopes(request.scopes());
    entity.setProjectIds(request.projectIds());
    entity.setReadOnly(request.readOnly());
    entity.setEnabled(true);

    apiKeyRepository.save(entity);

    ApiKeyResponse resp = ApiKeyResponse.fromEntity(entity);
    resp.setKey(generated.plainText());
    return resp;
  }

  /**
   * 重新生成指定的 API Key（轮换密钥），返回包含新明文的响应。
   */
  public ApiKeyResponse regenerate(String id) {
    AuthApiKey existing = apiKeyRepository.find(id);
    if (existing == null) {
      throw new IllegalArgumentException("API Key not found: " + id);
    }
    ApiKeyGenerator.GeneratedKey generated = ApiKeyGenerator.generate(existing.getKeyType());
    existing.setKeyHash(generated.hash());
    existing.setKeyPrefix(generated.prefix());
    apiKeyRepository.save(existing);

    ApiKeyResponse resp = ApiKeyResponse.fromEntity(existing);
    resp.setKey(generated.plainText());
    return resp;
  }

  public void delete(String id) {
    apiKeyRepository.delete(id);
  }

  /**
   * 验证 API Key 明文，返回对应的实体（如果有效）。
   */
  public AuthApiKey validate(String plainKey) {
    String hash = ApiKeyGenerator.sha256(plainKey);
    AuthApiKey apiKey = apiKeyRepository.findByKeyHash(hash);
    if (apiKey == null) {
      return null;
    }
    if (!apiKey.getEnabled()) {
      return null;
    }
    if (apiKey.getExpiresAt() != null && apiKey.getExpiresAt().isBefore(LocalDateTime.now())) {
      return null;
    }
    // 更新最后使用时间（异步即可，不阻塞认证）
    try {
      apiKeyRepository.updateLastUsedAt(apiKey.getId(), LocalDateTime.now());
    } catch (Exception e) {
      log.warn("Failed to update last_used_at for API Key {}", apiKey.getId(), e);
    }
    return apiKey;
  }
}
