package dev.flexmodel.common.config.web.filter;


import dev.flexmodel.codegen.entity.AuthApiKey;
import dev.flexmodel.codegen.entity.AuthProviderConfig;
import dev.flexmodel.codegen.entity.Project;
import dev.flexmodel.common.SessionContextHolder;
import dev.flexmodel.common.config.web.jwt.JwtUtil;
import dev.flexmodel.auth.exception.AuthException;
import dev.flexmodel.project.ProjectService;
import dev.flexmodel.auth.service.ApiKeyService;
import dev.flexmodel.projectauth.AuthProviderConfigService;
import dev.flexmodel.projectauth.provider.AuthContext;
import dev.flexmodel.projectauth.provider.AuthProvider;
import dev.flexmodel.projectauth.provider.AuthResult;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.*;

/**
 * 认证过滤器。
 * <p>
 * 认证链：PermitAll -> 系统 JWT -> API Key(fm_ak_ 前缀) -> 项目 Provider(OIDC/Script) -> 401
 *
 * @author cjbi
 */
@Slf4j
@Provider
public class AuthFilter implements ContainerRequestFilter {

  @Context
  ResourceInfo resourceInfo;
  @Inject
  ProjectService projectService;
  @Inject
  ApiKeyService apiKeyService;
  @Inject
  AuthProviderConfigService authProviderConfigService;

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    String path = requestContext.getUriInfo().getPath();
    boolean isFlexmodelPath = path.startsWith("/");
    if (!isFlexmodelPath) {
      return;
    }

    // 1. PermitAll -> 直接放行
    PermitAll permitAll = resourceInfo.getResourceMethod().getAnnotation(PermitAll.class);
    if (permitAll != null) {
      return;
    }

    // 2. 提取 Bearer token
    String accessToken = Objects.toString(requestContext.getHeaderString("Authorization"), "")
      .replaceFirst("Bearer ", "").trim();
    if (accessToken.isEmpty()) {
      throw new AuthException("Token is missing");
    }

    String projectId = requestContext.getUriInfo().getPathParameters().getFirst("projectId");

    // 3. 认证链
    if (trySystemJwt(accessToken, requestContext, projectId)) {
      return;
    }
    if (accessToken.startsWith("fm_ak_") && tryApiKey(accessToken, requestContext, projectId)) {
      return;
    }
    if (projectId != null && tryProjectProviders(accessToken, requestContext, projectId)) {
      return;
    }

    // 4. 全部失败 -> 401
    throw new AuthException("Invalid token");
  }

  /**
   * 尝试系统 JWT 验证（管理后台用户）。
   */
  private boolean trySystemJwt(String token, ContainerRequestContext requestContext, String projectId) {
    try {
      if (!JwtUtil.verify(token)) {
        return false;
      }
    } catch (Exception e) {
      return false;
    }
    String userId = JwtUtil.getAccount(token);
    fillSessionContextForUser(requestContext, projectId, userId);
    return true;
  }

  /**
   * 尝试 API Key 验证（fm_ak_ 前缀）。
   */
  private boolean tryApiKey(String token, ContainerRequestContext requestContext, String projectId) {
    AuthApiKey apiKey = apiKeyService.validate(token);
    if (apiKey == null) {
      return false;
    }
    // 系统级 Key（project_id 为空）：检查 project_ids 白名单
    if (!isProjectAllowed(apiKey, projectId)) {
      return false;
    }
    fillSessionContextForApiKey(requestContext, apiKey, projectId);
    return true;
  }

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

  /**
   * 系统 JWT 认证 -> 填充上下文（管理后台用户）。
   */
  private void fillSessionContextForUser(ContainerRequestContext requestContext, String projectId, String userId) {
    if (projectId != null) {
      Project project = projectService.findProject(projectId);
      if (project == null) {
        throw new AuthException("Project not found");
      }
      SessionContextHolder.setProjectId(projectId);
      SessionContextHolder.setProjectDatabaseName(projectService.resolveDatabaseName(projectId));
      SessionContextHolder.setBranchName(project.getCurrentBranch());
    }
    SessionContextHolder.setUserId(userId);
    SessionContextHolder.setCaller(userId);
    requestContext.setProperty("projectId", projectId);
    requestContext.setProperty("userId", userId);
  }

  /**
   * API Key 认证 -> 填充上下文。
   */
  private void fillSessionContextForApiKey(ContainerRequestContext requestContext, AuthApiKey apiKey, String requestProjectId) {
    String projectId = requestProjectId;
    if (projectId != null) {
      Project project = projectService.findProject(projectId);
      if (project == null) {
        throw new AuthException("Project not found");
      }
      SessionContextHolder.setProjectId(projectId);
      SessionContextHolder.setProjectDatabaseName(projectService.resolveDatabaseName(projectId));
      SessionContextHolder.setBranchName(project.getCurrentBranch());
    }
    SessionContextHolder.setCaller(apiKey.getName());
    requestContext.setProperty("projectId", projectId);
  }

  /**
   * 外部 Provider 认证 -> 填充上下文。
   */
  private void fillSessionContextForProvider(ContainerRequestContext requestContext, String projectId, AuthResult result) {
    Project project = projectService.findProject(projectId);
    if (project == null) {
      throw new AuthException("Project not found");
    }
    SessionContextHolder.setProjectId(projectId);
    SessionContextHolder.setProjectDatabaseName(project.getCurrentDatabaseName());
    SessionContextHolder.setBranchName(project.getCurrentBranch());
    SessionContextHolder.setCaller(result.getCaller());
    requestContext.setProperty("projectId", projectId);
  }

}
