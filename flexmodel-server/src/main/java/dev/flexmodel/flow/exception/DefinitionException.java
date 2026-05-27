package dev.flexmodel.flow.exception;

import dev.flexmodel.flow.common.ErrorEnum;

public class DefinitionException extends TurboException {

  public DefinitionException(int errNo, String errMsg) {
    super(errNo, errMsg);
  }

  public DefinitionException(ErrorEnum errorEnum) {
    super(errorEnum);
  }
}
