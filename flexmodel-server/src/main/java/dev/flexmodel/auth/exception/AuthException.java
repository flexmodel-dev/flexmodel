package dev.flexmodel.auth.exception;

import dev.flexmodel.common.BusinessException;

/**
 * @author cjbi
 */
public class AuthException extends BusinessException {

  public AuthException(String message) {
    super(message);
  }

  public AuthException(String message, Throwable cause) {
    super(message, cause);
  }
}
