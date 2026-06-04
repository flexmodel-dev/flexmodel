package dev.flexmodel.projectauth;

import dev.flexmodel.codegen.entity.AuthApiKey;

import java.util.List;

public interface ApiKeyRepository {

  List<AuthApiKey> findByProjectId(String projectId);

  AuthApiKey findByKeyHash(String keyHash);

  AuthApiKey find(String id);

  AuthApiKey save(AuthApiKey apiKey);

  void delete(String id);

  void deleteByProjectId(String projectId);

  void updateLastUsedAt(String id, java.time.LocalDateTime lastUsedAt);
}
