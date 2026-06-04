package dev.flexmodel.projectauth;

import dev.flexmodel.codegen.entity.AuthProviderConfig;
import dev.flexmodel.common.utils.JsonUtils;
import dev.flexmodel.projectauth.provider.AuthProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@ApplicationScoped
public class AuthProviderConfigService {

  @Inject
  AuthProviderConfigRepository authProviderConfigRepository;

  public List<AuthProviderConfig> listByProject(String projectId) {
    return authProviderConfigRepository.findByProjectId(projectId);
  }

  public AuthProviderConfig find(String projectId, String name) {
    return authProviderConfigRepository.find(projectId, name);
  }

  public AuthProviderConfig create(String projectId, AuthProviderConfig config) {
    config.setProjectId(projectId);
    return authProviderConfigRepository.save(config);
  }

  public AuthProviderConfig update(String projectId, String name, AuthProviderConfig config) {
    AuthProviderConfig existing = authProviderConfigRepository.find(projectId, name);
    if (existing == null) {
      throw new IllegalArgumentException("Auth provider not found: " + name);
    }
    config.setName(name);
    config.setProjectId(projectId);
    config.setCreatedAt(existing.getCreatedAt());
    return authProviderConfigRepository.save(config);
  }

  public void delete(String projectId, String name) {
    authProviderConfigRepository.delete(projectId, name);
  }

  public void deleteByProjectId(String projectId) {
    authProviderConfigRepository.deleteByProjectId(projectId);
  }

  /**
   * 从配置实体构建 AuthProvider 实例。
   */
  public AuthProvider buildProvider(AuthProviderConfig config) {
    if (config.getConfig() == null) {
      return null;
    }
    return JsonUtils.getInstance().convertValue(config.getConfig(), AuthProvider.class);
  }
}
