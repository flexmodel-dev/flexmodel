package dev.flexmodel.mcp;

import dev.flexmodel.apps.navigation.AppNavigationService;
import dev.flexmodel.codegen.entity.AuthApiKey;
import dev.flexmodel.common.SessionContext;
import dev.flexmodel.project.ProjectService;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;

import java.util.Arrays;

/**
 * 工具访问控制：同一套工具同时服务 MCP（API Key 服务身份）与页面 AI 助手（登录用户）。
 * <p>
 * MCP 请求由 {@link McpAuthHandler} 注入 API Key；页面 AI 请求无 Key，
 * 回退为当前登录用户，写操作要求应用所有者或管理员。
 */
@ApplicationScoped
public class McpToolAccess {
  static final String API_KEY_ATTRIBUTE = McpToolAccess.class.getName() + ".apiKey";

  @Inject
  RoutingContext request;
  @Inject
  SessionContext current;
  @Inject
  ProjectService projectService;
  @Inject
  AppNavigationService navigationService;

  /**
   * 全局工具（目录等）：MCP 要求 API Key，页面 AI 要求登录用户。
   */
  public void requireDirectory() {
    AuthApiKey key = request.get(API_KEY_ATTRIBUTE);
    if (key != null) {
      // A key is a service identity and must never impersonate an approving user.
      current.setUserId("api_key:" + key.getId());
      current.setAuthenticatedUser(false);
      return;
    }
    requireLogin();
  }

  /**
   * 应用级工具：MCP 校验 Key 的应用范围与只读限制；
   * 页面 AI 要求登录用户，写操作额外要求应用所有者或管理员。
   */
  public void requireApp(String appId, boolean write) {
    AuthApiKey key = request.get(API_KEY_ATTRIBUTE);
    if (key == null) {
      requireLogin();
      if (write && !navigationService.isOwnerOrAdmin(appId)) {
        throw new ForbiddenException("仅应用所有者或管理员可执行此操作");
      }
    } else {
      // A key is a service identity and must never impersonate an approving user.
      current.setUserId("api_key:" + key.getId());
      current.setAuthenticatedUser(false);
      String allowed = key.getProjectIds();
      if (allowed != null && !allowed.isBlank()
        && Arrays.stream(allowed.split(",")).map(String::trim).noneMatch(appId::equals)) {
        throw new ForbiddenException("API Key cannot access this app");
      }
      if (write && Boolean.TRUE.equals(key.getReadOnly())) {
        throw new ForbiddenException("Read-only API Key cannot modify approval flows");
      }
    }
    current.setProjectId(appId);
    current.setProjectDatabaseName(projectService.resolveDatabaseName(appId));
  }

  private void requireLogin() {
    if (!current.isAuthenticatedUser() || current.getUserId() == null || current.getUserId().isBlank()) {
      throw new NotAuthorizedException("请先登录");
    }
  }
}
