package dev.flexmodel.common.config.web.filter;


import dev.flexmodel.codegen.entity.Project;
import dev.flexmodel.idp.IdentityProviderService;
import dev.flexmodel.project.ProjectService;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;
import dev.flexmodel.settings.SettingsService;
import dev.flexmodel.codegen.entity.IdentityProvider;
import dev.flexmodel.auth.AuthException;
import dev.flexmodel.idp.provider.ValidateParam;
import dev.flexmodel.idp.provider.ValidateResult;
import dev.flexmodel.settings.Settings;
import dev.flexmodel.common.config.web.jwt.JwtUtil;
import dev.flexmodel.common.SessionContextHolder;
import dev.flexmodel.common.utils.JsonUtils;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @author cjbi
 */
@Slf4j
@Provider
public class AuthFilter implements ContainerRequestFilter {

  @Context
  ResourceInfo resourceInfo;
  @Inject
  SettingsService settingsService;
  @Inject
  IdentityProviderService identityProviderService;
  @Inject
  ProjectService projectService;

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    String path = requestContext.getUriInfo().getPath();
    boolean isFlexmodelPath = path.startsWith("/v1/");
    if (isFlexmodelPath) {
      PermitAll permitAll = resourceInfo.getResourceMethod().getAnnotation(PermitAll.class);
      if (permitAll == null) {
        String accessToken = Objects.toString(requestContext.getHeaderString("Authorization"), "").replaceFirst("Bearer ", "");
        if (accessToken.isEmpty()) {
          throw new AuthException("Token is missing");
        }
        // 默认使用内置的token，否则就使用idp提供的身份源验证
        if (!JwtUtil.verify(accessToken) && !varifyWithIdp("default", requestContext)) {
          throw new AuthException("Invalid token");
        }
        // 填充会话上下文
        fillSessionContext(requestContext);
      }
    }
  }

  /**
   * 使用idp验证
   *
   * @param requestContext 请求上下文
   * @return 是否验证成功
   */
  private boolean varifyWithIdp(String projectId, ContainerRequestContext requestContext) {
    Settings.Security security = settingsService.getSettings().getSecurity();
    String systemIdentityProvider = security.getSystemIdentityProvider();
    IdentityProvider identityProvider = identityProviderService.find(systemIdentityProvider);
    if (identityProvider == null) {
      log.warn("system idp not found!");
      return false;
    }
    dev.flexmodel.idp.provider.Provider provider = JsonUtils.getInstance().convertValue(identityProvider.getProvider(), dev.flexmodel.idp.provider.Provider.class);
    ValidateParam param = new ValidateParam();
    Map<String, String> headers = new HashMap<>();
    requestContext.getHeaders().forEach((k, v) -> headers.put(k, v.getFirst()));
    Collection<String> propertyNames = requestContext.getPropertyNames();
    Map<String, String> query = new HashMap<>();
    propertyNames.forEach(propertyName -> query.put(propertyName, Objects.toString(requestContext.getProperty(propertyName), null)));
    param.setQuery(query);
    param.setHeaders(headers);
    ValidateResult result = provider.validate(projectId, param);
    return result.isSuccess();
  }

  private void fillSessionContext(ContainerRequestContext requestContext) {
    String projectId = requestContext.getUriInfo().getPathParameters().getFirst("projectId");
    String accessToken = Objects.toString(requestContext.getHeaderString("Authorization"), "")
      .replaceFirst("Bearer ", "");
    String userId = JwtUtil.getAccount(accessToken);
    if (projectId != null) {
      Project project = projectService.findProject(projectId);
      if (project == null) {
        throw new AuthException("Project not found");
      }
      SessionContextHolder.setProjectId(projectId);
      String currentBranch = project.getCurrentBranch();
      if (!"main".equals(currentBranch)) {
        SessionContextHolder.setProjectDatabaseName(project.getDatabaseName() + "_" + currentBranch);
      } else {
        SessionContextHolder.setProjectDatabaseName(project.getDatabaseName());
      }
      SessionContextHolder.setBranchName(currentBranch);
    } else {
      SessionContextHolder.setProjectId(null);
      SessionContextHolder.setProjectDatabaseName(null);
    }
    SessionContextHolder.setUserId(userId);
    requestContext.setProperty("projectId", projectId);
    requestContext.setProperty("userId", userId);
  }

}
