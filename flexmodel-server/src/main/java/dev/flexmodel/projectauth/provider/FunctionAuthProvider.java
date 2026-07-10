package dev.flexmodel.projectauth.provider;

import dev.flexmodel.functions.FunctionService;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.ws.rs.core.Response;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * 云函数认证提供商。
 * 调用配置的云函数进行自定义认证。
 * 函数返回 HTTP 200 即认证成功，其他状态码视为认证失败。
 */
@Getter
@Setter
@Slf4j
public class FunctionAuthProvider implements AuthProvider {

  private String functionName;

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
        return AuthResult.ok("function-user", java.util.Set.of("read"));
      } else {
        return AuthResult.fail("Function auth failed with status: " + status);
      }
    } catch (Exception e) {
      log.error("Function auth error: {}", e.getMessage(), e);
      return AuthResult.fail("Function error: " + e.getMessage());
    }
  }
}
