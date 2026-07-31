package dev.flexmodel.common;

public class InternalServerException extends BusinessException {
  public InternalServerException(String message) {
    super(message);
  }

  public InternalServerException(String message, Throwable cause) {
    super(message, cause);
  }
}
