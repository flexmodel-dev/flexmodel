package dev.flexmodel.projectauth;

import dev.flexmodel.auth.service.ApiKeyService;
import dev.flexmodel.auth.service.InternalTokenService;
import dev.flexmodel.codegen.entity.AuthApiKey;
import dev.flexmodel.codegen.entity.AuthProviderConfig;
import dev.flexmodel.common.config.web.jwt.JwtService;
import dev.flexmodel.projectauth.provider.AuthContext;
import dev.flexmodel.projectauth.provider.AuthProvider;
import dev.flexmodel.projectauth.provider.AuthResult;
import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Edge token validation endpoint.
 * <p>
 * Called by the Deno runtime to validate tokens for external edge function invocations.
 * The authentication chain mirrors {@link dev.flexmodel.common.config.web.filter.AuthFilter}:
 * <ol>
 *   <li>Invoke-token JWT (account = "svc:invoke")</li>
 *   <li>API Key (prefix = "fm_ak_")</li>
 *   <li>Project IdP (OIDC / Function) — only if configured</li>
 *   <li>No providers configured → anonymous access (no auth required)</li>
 * </ol>
 * <p>
 * All auth secrets stay in Java — the Deno runtime no longer needs JWT_SECRET.
 *
 * @author cjbi
 */
@Slf4j
@ApplicationScoped
@Path("/edge")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class EdgeValidateResource {

  @Inject
  JwtService jwtService;

  @Inject
  ApiKeyService apiKeyService;

  @Inject
  InternalTokenService internalTokenService;

  @Inject
  AuthProviderConfigService authProviderConfigService;

  /**
   * Validate an edge token and return the auth context.
   */
  @POST
  @Path("/validate")
  @PermitAll
  public EdgeValidateResponse validate(EdgeValidateRequest request) {
    String token = request.token();
    String projectId = request.projectId();
    String functionName = request.functionName();

    // --- 1. Invoke-token JWT ---
    if (token != null && !token.isBlank() && !token.startsWith("fm_ak_")) {
      EdgeValidateResponse jwtResult = tryInvokeToken(token, projectId);
      if (jwtResult != null) return jwtResult;
    }

    // --- 2. API Key ---
    if (token != null && token.startsWith("fm_ak_")) {
      EdgeValidateResponse apiKeyResult = tryApiKey(token, projectId, functionName);
      if (apiKeyResult != null) return apiKeyResult;
      // API Key invalid → don't fall through to providers
      return EdgeValidateResponse.invalid();
    }

    // --- 3. Project IdP (OIDC / Function auth providers) ---
    if (projectId != null && !projectId.isBlank()) {
      EdgeValidateResponse providerResult = tryProjectProviders(token, projectId, functionName);
      if (providerResult != null) return providerResult;
    }

    // --- 4. No auth method succeeded → invalid ---
    return EdgeValidateResponse.invalid();
  }

  // ============================================================
  // Invoke-token JWT
  // ============================================================

  private EdgeValidateResponse tryInvokeToken(String token, String projectId) {
    try {
      if (!jwtService.verify(token)) return null;

      String account = jwtService.getAccount(token);
      if (!"svc:invoke".equals(account)) return null;

      String claimProjectId = jwtService.getClaim(token, "projectId");
      String claimFunctionName = jwtService.getClaim(token, "functionName");
      String authToken = jwtService.getClaim(token, "authToken");

      if (claimProjectId == null || claimFunctionName == null
        || authToken == null) {
        return null;
      }

      log.debug("Edge auth: invoke-token validated for {}:{}", claimProjectId, claimFunctionName);
      return EdgeValidateResponse.valid(
        claimProjectId, claimFunctionName, authToken, "invoke-token", "svc:invoke"
      );

    } catch (Exception e) {
      log.debug("Edge auth: JWT verification failed: {}", e.getMessage());
      return null;
    }
  }

  // ============================================================
  // API Key
  // ============================================================

  private EdgeValidateResponse tryApiKey(String token, String projectId, String functionName) {
    AuthApiKey apiKey = apiKeyService.validate(token);
    if (apiKey == null) return null;

    String authToken = internalTokenService.signToken(projectId);

    log.debug("Edge auth: API key validated for project {}", projectId);
    return EdgeValidateResponse.valid(
      projectId, functionName, authToken, "api-key", apiKey.getName()
    );
  }

  // ============================================================
  // Project IdP Providers
  // ============================================================

  private EdgeValidateResponse tryProjectProviders(String token, String projectId, String functionName) {
    List<AuthProviderConfig> configs = authProviderConfigService.listByProject(projectId);

    // --- No providers configured → anonymous access ---
    if (configs == null || configs.isEmpty()) {
      String authToken = internalTokenService.signToken(projectId);
      log.debug("Edge auth: no IdP configured for {} → anonymous access", projectId);
      return EdgeValidateResponse.valid(
        projectId, functionName, authToken, "anonymous", "anonymous"
      );
    }

    // Skip if no token provided (can't authenticate against providers without a token)
    if (token == null || token.isBlank()) {
      log.debug("Edge auth: providers configured but no token for {}", projectId);
      return null;
    }

    // Try each enabled provider
    AuthContext authContext = new AuthContext();
    authContext.setProjectId(projectId);
    authContext.setBearerToken(token);

    for (AuthProviderConfig config : configs) {
      if (!config.getEnabled()) continue;

      try {
        AuthProvider provider = authProviderConfigService.buildProvider(config);
        if (provider == null) continue;

        AuthResult result = provider.authenticate(authContext);
        if (result != null && result.isSuccess()) {
          String authToken = internalTokenService.signToken(projectId);
          log.debug("Edge auth: IdP '{}' validated for {} → user {}", config.getName(), projectId, result.getUserId());
          return EdgeValidateResponse.valid(
            projectId, functionName, authToken, "idp", result.getUserId()
          );
        }
      } catch (Exception e) {
        log.debug("Edge auth: IdP '{}' failed for {}: {}", config.getName(), projectId, e.getMessage());
      }
    }

    return null;
  }
}
