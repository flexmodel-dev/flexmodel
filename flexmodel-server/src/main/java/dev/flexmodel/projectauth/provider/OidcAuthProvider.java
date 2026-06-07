package dev.flexmodel.projectauth.provider;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import dev.flexmodel.JsonUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * OIDC 认证提供商。
 * 通过 token introspection 验证 Bearer Token，提取 sub 和 scope。
 */
@Getter
@Setter
@Slf4j
public class OidcAuthProvider implements AuthProvider {

  private String issuer;
  private String clientId;
  private String clientSecret;

  @Override
  public String getType() {
    return "oidc";
  }

  @Override
  public AuthResult authenticate(AuthContext context) {
    String token = context.getBearerToken();
    if (token == null || token.isEmpty()) {
      return AuthResult.fail("Missing bearer token");
    }
    try {
      return introspect(token);
    } catch (Exception e) {
      log.error("OIDC introspection error: {}", e.getMessage(), e);
      return AuthResult.fail("OIDC introspection failed: " + e.getMessage());
    }
  }

  @SuppressWarnings("all")
  private AuthResult introspect(String token) throws IOException, InterruptedException {
    Map<String, Object> discovery = getDiscovery();
    String introspectionEndpoint = (String) discovery.get("introspection_endpoint");
    if (introspectionEndpoint == null) {
      return AuthResult.fail("No introspection_endpoint found in OIDC discovery");
    }

    String paramString = Map.of(
      "token", token,
      "token_type_hint", "access_token",
      "client_id", clientId,
      "client_secret", clientSecret
    ).entrySet().stream()
      .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
      .collect(Collectors.joining("&"));

    HttpRequest request = HttpRequest.newBuilder()
      .uri(URI.create(introspectionEndpoint))
      .headers("Content-Type", "application/x-www-form-urlencoded")
      .POST(HttpRequest.BodyPublishers.ofString(paramString))
      .build();

    HttpClient client = HttpClient.newHttpClient();
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    String body = response.body();
    log.debug("OIDC introspect response: {}", body);

    Map<?, ?> result = JsonUtils.parseToObject(body, Map.class);
    Boolean active = (Boolean) result.get("active");
    if (active == null || !active) {
      return AuthResult.fail("Token is not active");
    }

    String sub = Objects.toString(result.get("sub"), "oidc-user");
    Set<String> scopes = parseScopes(result.get("scope"));
    return AuthResult.ok(sub, scopes);
  }

  private Set<String> parseScopes(Object scopeObj) {
    if (scopeObj == null) return Set.of();
    if (scopeObj instanceof String s) {
      return Set.of(s.split("\\s+"));
    }
    if (scopeObj instanceof List<?> list) {
      return list.stream().map(Object::toString).collect(Collectors.toSet());
    }
    return Set.of();
  }

  @SuppressWarnings("all")
  private Map<String, Object> getDiscovery() throws IOException, InterruptedException {
    HttpClient client = HttpClient.newHttpClient();
    HttpResponse<String> response = client.send(
      HttpRequest.newBuilder()
        .uri(URI.create(issuer + "/.well-known/openid-configuration"))
        .GET()
        .build(),
      HttpResponse.BodyHandlers.ofString()
    );
    return JsonUtils.parseToObject(response.body(), Map.class);
  }
}
