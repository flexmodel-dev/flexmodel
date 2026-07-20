package dev.flexmodel.projectauth.provider;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.functions.FunctionService;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.ws.rs.core.Response;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 云函数认证提供商。
 * 调用配置的云函数进行自定义认证。
 * 函数返回 HTTP 200 即认证成功，从 response body 中提取 userId；
 * 其他状态码视为认证失败。
 */
@Getter
@Setter
@Slf4j
public class FunctionAuthProvider implements AuthProvider {

  private String functionName;

  /**
   * 权限范围配置：同 OidcAuthProvider，为 {@code null} 或空表示"全部范围"。
   */
  private Set<String> permissions = Set.of("*");

  @Override
  public String getType() {
    return "function";
  }

  @Override
  public AuthResult authenticate(AuthContext context) {
    try {
      FunctionService functionService = CDI.current().select(FunctionService.class).get();

      Response response = functionService.invoke(context.getProjectId(), functionName, context);
      int status = response.getStatus();

      if (status == 200) {
        String userId = extractUserId(response);
        AuthResult authResult = AuthResult.ok(userId);
        authResult.setPermissions(resolvePermissions());
        return authResult;
      } else {
        return AuthResult.fail("Function auth failed with status: " + status);
      }
    } catch (Exception e) {
      log.error("Function auth error: {}", e.getMessage(), e);
      return AuthResult.fail("Function error: " + e.getMessage());
    }
  }

  private Set<String> resolvePermissions() {
    Set<String> perms = new HashSet<>();
    for (String p : permissions) {
      if (p != null && !p.isBlank()) {
        perms.add(p.trim());
      }
    }
    return perms.isEmpty() ? Set.of("*") : perms;
  }

  /**
   * 从云函数 response body 中提取 userId。
   * 约定函数返回 JSON 格式：{ "userId": "xxx", ... }
   * 若 body 中无 userId 字段，则 fallback 为 "function-user"。
   */
  @SuppressWarnings("unchecked")
  private String extractUserId(Response response) {
    try {
      String body = response.readEntity(String.class);
      if (body == null || body.isEmpty()) {
        return "function-user";
      }
      Map<String, Object> result = JsonUtils.parseToObject(body, Map.class);
      if (result == null) {
        return "function-user";
      }
      Object userIdObj = result.get("userId");
      if (userIdObj != null) {
        return userIdObj.toString();
      }
    } catch (Exception e) {
      log.warn("Failed to extract userId from function response, using fallback", e);
    }
    return "function-user";
  }
}
