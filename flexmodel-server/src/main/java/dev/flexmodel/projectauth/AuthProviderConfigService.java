package dev.flexmodel.projectauth;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.codegen.entity.AuthProviderConfig;
import dev.flexmodel.projectauth.provider.AuthProvider;
import io.quarkus.cache.CacheInvalidate;
import io.quarkus.cache.CacheKey;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@ApplicationScoped
public class AuthProviderConfigService {

  @Inject
  AuthProviderConfigRepository authProviderConfigRepository;

  @CacheResult(cacheName = "auth-config-cache")
  public List<AuthProviderConfig> listByProject(String projectId) {
    return authProviderConfigRepository.findByProjectId(projectId);
  }

  public AuthProviderConfig find(String projectId, String name) {
    return authProviderConfigRepository.find(projectId, name);
  }

  @CacheInvalidate(cacheName = "auth-config-cache")
  public AuthProviderConfig create(@CacheKey String projectId, AuthProviderConfig config) {
    disableOtherProviders(projectId, null);
    config.setEnabled(true);
    return authProviderConfigRepository.save(projectId, config);
  }

  @CacheInvalidate(cacheName = "auth-config-cache")
  public AuthProviderConfig update(@CacheKey String projectId, String name, AuthProviderConfig config) {
    config.setName(name);
    AuthProviderConfig existing = authProviderConfigRepository.find(projectId, name);
    if (existing == null) {
      return authProviderConfigRepository.save(projectId, config);
    }
    config.setCreatedAt(existing.getCreatedAt());
    if (config.getEnabled()) {
      disableOtherProviders(projectId, name);
    }
    return authProviderConfigRepository.save(projectId, config);
  }

  @CacheInvalidate(cacheName = "auth-config-cache")
  public void delete(@CacheKey String projectId, String name) {
    authProviderConfigRepository.delete(projectId, name);
  }

  /**
   * 从配置实体构建 AuthProvider 实例。
   */
  public AuthProvider buildProvider(AuthProviderConfig config) {
    if (config.getConfig() == null) {
      return null;
    }
    return JsonUtils.convertValue(config.getConfig(), AuthProvider.class);
  }

  /**
   * 禁用项目中除 excludeName 以外的所有已启用 Provider，确保同一时间只有一个认证方式生效。
   */
  private void disableOtherProviders(String projectId, String excludeName) {
    List<AuthProviderConfig> existing = authProviderConfigRepository.findByProjectId(projectId);
    for (AuthProviderConfig p : existing) {
      if (p.getEnabled() && (excludeName == null || !p.getName().equals(excludeName))) {
        p.setEnabled(false);
        authProviderConfigRepository.save(projectId, p);
      }
    }
  }
}
