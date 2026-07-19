package dev.flexmodel.projectauth.provider;

import lombok.Getter;
import lombok.Setter;

import java.util.Set;

/**
 * 认证结果。
 */
@Getter
@Setter
public class AuthResult {
  private boolean success;
  private String userId;
  private String message;
  /**
   * 认证后授予调用方的权限串集合。由认证 Provider 根据其配置决定：
   * 未配置权限范围时为 {@code ["*"]}（全部范围），否则为配置中勾选的权限串。
   * 该集合会透传至 {@link dev.flexmodel.common.SessionContext#setPermissions(Set)}，
   * 供后续 {@code @RequiresPermissions} 鉴权使用。
   */
  private Set<String> permissions;

  public AuthResult(boolean success, String userId, String message) {
    this.success = success;
    this.userId = userId;
    this.message = message;
  }

  public static AuthResult fail(String message) {
    return new AuthResult(false, null, message);
  }

  public static AuthResult ok(String userId) {
    return new AuthResult(true, userId, "success");
  }
}
