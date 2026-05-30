package dev.flexmodel.flow.exception;

import dev.flexmodel.flow.common.ErrorEnum;

public class ReentrantException extends ProcessException {

  public ReentrantException(int errNo, String errMsg) {
    super(errNo, errMsg);
  }

  public ReentrantException(ErrorEnum errorEnum) {
    super(errorEnum);
  }
}
