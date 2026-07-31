package dev.flexmodel.pages;

import dev.flexmodel.common.BusinessException;

/**
 * @author cjbi
 */
public class PageException extends BusinessException {

  public PageException(String message) {
    super(message);
  }

  public PageException(String message, Throwable cause) {
    super(message, cause);
  }
}
