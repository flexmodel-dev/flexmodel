package dev.flexmodel.flow.dto.result;

import dev.flexmodel.flow.dto.bo.ElementInstance;
import dev.flexmodel.flow.common.ErrorEnum;

import java.util.List;

public class ElementInstanceListResult extends CommonResult {
  private List<ElementInstance> elementInstanceList;

  public ElementInstanceListResult(ErrorEnum errorEnum) {
    super(errorEnum);
  }

  public List<ElementInstance> getElementInstanceList() {
    return elementInstanceList;
  }

  public void setElementInstanceList(List<ElementInstance> elementInstanceList) {
    this.elementInstanceList = elementInstanceList;
  }

  @Override
  public String toString() {
    return "ElementInstanceListResult{" +
           "elementInstanceList=" + elementInstanceList +
           '}';
  }
}
