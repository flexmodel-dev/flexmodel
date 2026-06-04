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
  private String caller;
  private Set<String> scopes;
  private String message;

  public AuthResult(boolean success, String caller, Set<String> scopes, String message) {
    this.success = success;
    this.caller = caller;
    this.scopes = scopes;
    this.message = message;
  }

  public static AuthResult fail(String message) {
    return new AuthResult(false, null, null, message);
  }

  public static AuthResult ok(String caller, Set<String> scopes) {
    return new AuthResult(true, caller, scopes, "success");
  }
}
