package dev.flexmodel.projectauth.provider;

/**
 * 认证提供商接口。
 * 每种认证方式（OIDC、Script 等）实现此接口。
 */
public interface AuthProvider {

  String getType();

  /**
   * 验证请求，返回认证结果（包含调用方标识和权限范围）。
   */
  AuthResult authenticate(AuthContext context);
}
