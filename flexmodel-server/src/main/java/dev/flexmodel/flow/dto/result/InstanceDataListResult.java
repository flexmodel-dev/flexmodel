package dev.flexmodel.flow.dto.result;

import dev.flexmodel.flow.common.ErrorEnum;

import java.util.Map;

public class InstanceDataListResult extends CommonResult {
  private Map<String, Object> variables;

  public InstanceDataListResult(ErrorEnum errorEnum) {
    super(errorEnum);
  }

  public Map<String, Object> getVariables() {
    return variables;
  }

  public void setVariables(Map<String, Object> variables) {
    this.variables = variables;
  }

  @Override
  public String toString() {
    return "InstanceDataListResult{" +
           "variables=" + variables +
           '}';
  }
}
