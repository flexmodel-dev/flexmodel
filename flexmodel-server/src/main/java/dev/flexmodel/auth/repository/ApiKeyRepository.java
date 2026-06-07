package dev.flexmodel.auth.repository;

import dev.flexmodel.codegen.entity.AuthApiKey;

import java.util.List;

public interface ApiKeyRepository {

  List<AuthApiKey> findAll();

  AuthApiKey findByKeyHash(String keyHash);

  AuthApiKey find(String id);

  AuthApiKey save(AuthApiKey apiKey);

  void delete(String id);

  void updateLastUsedAt(String id, java.time.LocalDateTime lastUsedAt);
}
