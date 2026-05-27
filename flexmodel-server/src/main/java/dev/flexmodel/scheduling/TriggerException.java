package dev.flexmodel.scheduling;

import dev.flexmodel.common.BusinessException;

/**
 * @author cjbi
 */
public class TriggerException extends BusinessException {

  public TriggerException(String message) {
    super(message);
  }

  public TriggerException(String message, Throwable cause) {
    super(message, cause);
  }
}
