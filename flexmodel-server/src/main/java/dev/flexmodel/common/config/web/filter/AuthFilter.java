package dev.flexmodel.common.config.web.filter;

import dev.flexmodel.auth.exception.AuthException;
import dev.flexmodel.auth.service.ApiKeyService;
import dev.flexmodel.codegen.entity.AuthApiKey;
import dev.flexmodel.codegen.entity.AuthProviderConfig;
import dev.flexmodel.codegen.entity.Bucket;
import dev.flexmodel.codegen.entity.Project;
import dev.flexmodel.codegen.enumeration.BucketVisibility;
import dev.flexmodel.common.SessionContext;
import dev.flexmodel.common.config.web.jwt.JwtService;
import dev.flexmodel.project.ProjectService;
import dev.flexmodel.projectauth.AuthProviderConfigService;
import dev.flexmodel.projectauth.provider.AuthContext;
import dev.flexmodel.projectauth.provider.AuthProvider;
import dev.flexmodel.projectauth.provider.AuthResult;
import dev.flexmodel.storage.BucketService;
import jakarta.annotation.Priority;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.*;

/**
 * 认证过滤器 — 控制面/数据面分层鉴权。
 * <p>
 * 根据请求路径区分 API surface：
 * <ul>
 *   <li><b>OPEN surface</b>（路径以 {@code open/} 开头）：允许 open scope API Key + 项目 IdP + 匿名（无 IdP 时）</li>
 *   <li><b>ADMIN surface</b>（其余路径）：允许系统 JWT + admin scope API Key</li>
 * </ul>
 * 认证链在 surface 内短路，跨 surface 的凭证一律拒绝。
 *
 * @author cjbi
 */
@Slf4j
@Provider
@Priority(Priorities.AUTHENTICATION)
public class AuthFilter implements ContainerRequestFilter, ContainerResponseFilter {

  @Context
  ResourceInfo resourceInfo;
  @Inject
  ProjectService projectService;
  @Inject
  ApiKeyService apiKeyService;
  @Inject
  AuthProviderConfigService authProviderConfigService;
  @Inject
  JwtService jwtService;
  @Inject
  SessionContext sessionContext;
  @Inject
  BucketService bucketService;

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    String path = requestContext.getUriInfo().getPath();
    // Normalize: remove leading slash for consistent prefix matching
    String normalizedPath = path != null ? path.replaceFirst("^/+", "") : "";

    // 1. PermitAll -> 直接放行
    PermitAll permitAll = resourceInfo.getResourceMethod().getAnnotation(PermitAll.class);
    if (permitAll != null) {
      return;
    }

    // 2. 提取 Bearer token
    String accessToken = Objects.toString(requestContext.getHeaderString("Authorization"), "")
        .replaceFirst("Bearer ", "").trim();

    // 3. 根据路径判断 API surface
    boolean isOpenSurface = normalizedPath.startsWith("open/");

    String projectId = requestContext.getUriInfo().getPathParameters().getFirst("projectId");

    if (isOpenSurface) {
      // ---- OPEN surface: API Key (open scope) + system JWT + IdP + anonymous ----
      if (accessToken.isEmpty()) {
        // Try IdP first (anonymous allowed if no IdP configured)
        if (tryProjectProviders(accessToken, requestContext, projectId)) {
          return;
        }
        // Public Bucket anonymous read
        if (isAnonymousPublicBucketRead(requestContext)) {
          return;
        }
        throw new AuthException("Token is missing");
      }

      // API Key (must be open scope)
      if (accessToken.startsWith("fm_ak_")) {
        if (tryOpenApiKey(accessToken, requestContext, projectId)) {
          return;
        }
        throw new AuthException("Invalid or unauthorized API key");
      }

      // System JWT (service accounts, e.g. Deno runtime callback)
      if (trySystemJwt(accessToken, requestContext, projectId)) {
        return;
      }

      // IdP token
      if (projectId != null && tryProjectProviders(accessToken, requestContext, projectId)) {
        return;
      }

      // Public Bucket anonymous read fallback
      if (isAnonymousPublicBucketRead(requestContext)) {
        return;
      }
      throw new AuthException("Invalid token");
    } else {
      // ---- ADMIN surface: system JWT + admin scope API Key ----
      if (accessToken.isEmpty()) {
        // Public Bucket anonymous read (admin surface still allows this)
        if (isAnonymousPublicBucketRead(requestContext)) {
          return;
        }
        throw new AuthException("Token is missing");
      }

      // System JWT
      if (trySystemJwt(accessToken, requestContext, projectId)) {
        return;
      }
      // API Key (must be admin scope)
      if (accessToken.startsWith("fm_ak_") && tryAdminApiKey(accessToken, requestContext, projectId)) {
        return;
      }
      throw new AuthException("Invalid token");
    }
  }

  // ============================================================
  // ADMIN surface auth methods
  // ============================================================

  /**
   * 尝试系统 JWT 验证（管理后台用户）。
   */
  private boolean trySystemJwt(String token, ContainerRequestContext requestContext, String projectId) {
    try {
      if (!jwtService.verify(token)) {
        return false;
      }
    } catch (Exception e) {
      return false;
    }
    String userId = jwtService.getAccount(token);
    fillSessionContextForUser(requestContext, projectId, userId);
    return true;
  }

  /**
   * 尝试 admin scope API Key 验证。
   */
  private boolean tryAdminApiKey(String token, ContainerRequestContext requestContext, String projectId) {
    AuthApiKey apiKey = apiKeyService.validate(token);
    if (apiKey == null) {
      return false;
    }
    if (!"admin".equals(apiKey.getScope())) {
      return false; // open scope key cannot access admin surface
    }
    if (!isProjectAllowed(apiKey, projectId)) {
      return false;
    }
    fillSessionContextForApiKey(requestContext, apiKey, projectId);
    return true;
  }

  // ============================================================
  // OPEN surface auth methods
  // ============================================================

  /**
   * 尝试 open scope API Key 验证。
   */
  private boolean tryOpenApiKey(String token, ContainerRequestContext requestContext, String projectId) {
    AuthApiKey apiKey = apiKeyService.validate(token);
    if (apiKey == null) {
      return false;
    }
    if (!"open".equals(apiKey.getScope())) {
      return false; // admin scope key cannot access open surface
    }
    if (!isProjectAllowed(apiKey, projectId)) {
      return false;
    }
    fillSessionContextForApiKey(requestContext, apiKey, projectId);
    return true;
  }

  // ============================================================
  // Shared auth methods
  // ============================================================

  /**
   * 检查系统级 API Key 是否允许访问指定项目。
   */
  private boolean isProjectAllowed(AuthApiKey apiKey, String projectId) {
    if (projectId == null) {
      return true;
    }
    String projectIds = apiKey.getProjectIds();
    if (projectIds == null || projectIds.isBlank()) {
      return true; // 空表示可访问所有项目
    }
    return Set.of(projectIds.split(",")).contains(projectId);
  }

  /**
   * 尝试项目级外部 Provider 验证。
   */
  private boolean tryProjectProviders(String token, ContainerRequestContext requestContext, String projectId) {
    List<AuthProviderConfig> configs = authProviderConfigService.listByProject(projectId);
    if (configs == null || configs.isEmpty()) {
      // No providers configured → anonymous access (open surface only)
      if (token == null || token.isBlank()) {
        fillSessionContextForAnonymous(requestContext, projectId);
        return true;
      }
      return false;
    }

    if (token == null || token.isBlank()) {
      return false;
    }

    AuthContext authContext = buildAuthContext(projectId, token, requestContext);

    for (AuthProviderConfig config : configs) {
      if (!config.getEnabled()) {
        continue;
      }
      try {
        AuthProvider provider = authProviderConfigService.buildProvider(config);
        if (provider == null) {
          continue;
        }
        AuthResult result = provider.authenticate(authContext);
        if (result != null && result.isSuccess()) {
          fillSessionContextForProvider(requestContext, projectId, result);
          return true;
        }
      } catch (Exception e) {
        log.debug("Auth provider '{}' failed: {}", config.getName(), e.getMessage());
      }
    }
    return false;
  }

  private AuthContext buildAuthContext(String projectId, String token, ContainerRequestContext requestContext) {
    AuthContext ctx = new AuthContext();
    ctx.setProjectId(projectId);
    ctx.setBearerToken(token);
    ctx.setMethod(requestContext.getMethod());
    ctx.setUrl(requestContext.getUriInfo().getRequestUri().toString());

    Map<String, String> headers = new HashMap<>();
    requestContext.getHeaders().forEach((k, v) -> headers.put(k, v.getFirst()));
    ctx.setHeaders(headers);

    Map<String, String> query = new HashMap<>();
    requestContext.getUriInfo().getQueryParameters().forEach((k, v) -> {
      if (v != null && !v.isEmpty()) {
        query.put(k, v.getFirst());
      }
    });
    ctx.setQuery(query);

    return ctx;
  }

  // ============================================================
  // Session context fillers
  // ============================================================

  /**
   * 系统 JWT 认证 -> 填充上下文（管理后台用户）。
   */
  private void fillSessionContextForUser(ContainerRequestContext requestContext, String projectId, String userId) {
    if (projectId != null) {
      Project project = projectService.findProject(projectId);
      if (project == null) {
        throw new AuthException("Project not found");
      }
      sessionContext.setProjectId(projectId);
      sessionContext.setProjectDatabaseName(projectService.resolveDatabaseName(projectId));
    }
    sessionContext.setUserId(userId);
    requestContext.setProperty("projectId", projectId);
    requestContext.setProperty("userId", userId);
  }

  /**
   * API Key 认证 -> 填充上下文。
   */
  private void fillSessionContextForApiKey(ContainerRequestContext requestContext, AuthApiKey apiKey,
                                           String projectId) {
    if (projectId != null) {
      Project project = projectService.findProject(projectId);
      if (project == null) {
        throw new AuthException("Project not found");
      }
      sessionContext.setProjectId(projectId);
      sessionContext.setProjectDatabaseName(projectService.resolveDatabaseName(projectId));
    }
    sessionContext.setUserId(apiKey.getName());
    requestContext.setProperty("projectId", projectId);
  }

  /**
   * 外部 Provider 认证 -> 填充上下文。
   */
  private void fillSessionContextForProvider(ContainerRequestContext requestContext, String projectId,
      AuthResult result) {
    Project project = projectService.findProject(projectId);
    if (project == null) {
      throw new AuthException("Project not found");
    }
    sessionContext.setProjectId(projectId);
    sessionContext.setProjectDatabaseName(project.getDatabaseName());
    sessionContext.setUserId(result.getUserId());
    sessionContext.setPermissions(result.getPermissions());
    requestContext.setProperty("projectId", projectId);
  }

  /**
   * 匿名访问 -> 填充上下文（仅 open surface，无 IdP 配置时）。
   */
  private void fillSessionContextForAnonymous(ContainerRequestContext requestContext, String projectId) {
    if (projectId != null) {
      Project project = projectService.findProject(projectId);
      if (project == null) {
        throw new AuthException("Project not found");
      }
      sessionContext.setProjectId(projectId);
      sessionContext.setProjectDatabaseName(projectService.resolveDatabaseName(projectId));
    }
    sessionContext.setUserId("anonymous");
    requestContext.setProperty("projectId", projectId);
  }

  /**
   * 判断匿名请求是否可访问公开 Bucket 的对象读接口（GET/HEAD）。
   */
  private boolean isAnonymousPublicBucketRead(ContainerRequestContext requestContext) {
    String method = requestContext.getMethod();
    if (!"GET".equals(method) && !"HEAD".equals(method)) {
      return false;
    }
    String path = requestContext.getUriInfo().getPath();
    if (path == null || !path.matches("(?i)^/?projects/[^/]+/buckets/[^/]+/objects/.+")) {
      return false;
    }
    String projectId = requestContext.getUriInfo().getPathParameters().getFirst("projectId");
    String bucketName = requestContext.getUriInfo().getPathParameters().getFirst("bucketName");
    if (projectId == null || bucketName == null) {
      return false;
    }
    try {
      return bucketService.getBucket("PROJECT", projectId, bucketName)
        .map(Bucket::getVisibility)
        .filter(BucketVisibility.PUBLIC::equals)
        .isPresent();
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
      throws IOException {
    // CDI @RequestScoped 自动管理生命周期，无需手动 clear
  }

}
