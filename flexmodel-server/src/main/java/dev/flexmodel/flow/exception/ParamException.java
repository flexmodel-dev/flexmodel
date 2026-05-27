package dev.flexmodel.flow.exception;

import dev.flexmodel.flow.common.ErrorEnum;

public class ParamException extends TurboException {

  public ParamException(int errNo, String errMsg) {
    super(errNo, errMsg);
  }

  public ParamException(ErrorEnum errorEnum) {
    super(errorEnum);
  }
}

