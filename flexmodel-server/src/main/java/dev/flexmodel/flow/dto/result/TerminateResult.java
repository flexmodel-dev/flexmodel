package dev.flexmodel.flow.dto.result;

import dev.flexmodel.flow.common.ErrorEnum;

public class TerminateResult extends RuntimeResult {

  public TerminateResult(ErrorEnum errorEnum) {
    super(errorEnum);
  }

}
