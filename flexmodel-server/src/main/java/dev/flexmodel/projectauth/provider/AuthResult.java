package dev.flexmodel.projectauth.provider;

import lombok.Getter;
import lombok.Setter;

/**
 * 认证结果。
 */
@Getter
@Setter
public class AuthResult {
  private boolean success;
  private String userId;
  private String message;

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
