package dev.flexmodel.projectauth;

import dev.flexmodel.codegen.entity.AuthProviderConfig;

import java.util.List;

public interface AuthProviderConfigRepository {

  List<AuthProviderConfig> findByProjectId(String projectId);

  AuthProviderConfig find(String projectId, String name);

  AuthProviderConfig save(AuthProviderConfig config);

  void delete(String projectId, String name);

  void deleteByProjectId(String projectId);
}
