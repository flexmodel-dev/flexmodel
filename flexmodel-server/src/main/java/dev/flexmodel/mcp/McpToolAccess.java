package dev.flexmodel.mcp;

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
 * Propagates the authenticated HTTP request into application services.
 */
@ApplicationScoped
public class McpToolAccess {
  static final String API_KEY_ATTRIBUTE = McpToolAccess.class.getName() + ".apiKey";

  @Inject
  RoutingContext request;
  @Inject
  SessionContext current;
  @Inject
  ProjectService projects;

  public void requireDirectory() {
    AuthApiKey key = request.get(API_KEY_ATTRIBUTE);
    if (key == null) throw new NotAuthorizedException("API Key required");
    // A key is a service identity and must never impersonate an approving user.
    current.setUserId("api_key:" + key.getId());
    current.setAuthenticatedUser(false);
  }

  public void requireApp(String appId, boolean write) {
    requireDirectory();
    AuthApiKey key = request.get(API_KEY_ATTRIBUTE);
    String allowed = key.getProjectIds();
    if (allowed != null && !allowed.isBlank()
      && Arrays.stream(allowed.split(",")).map(String::trim).noneMatch(appId::equals)) {
      throw new ForbiddenException("API Key cannot access this app");
    }
    if (write && Boolean.TRUE.equals(key.getReadOnly())) {
      throw new ForbiddenException("Read-only API Key cannot modify approval flows");
    }
    current.setProjectId(appId);
    current.setProjectDatabaseName(projects.resolveDatabaseName(appId));
  }
}
