package dev.flexmodel.flow.dto.result;

import dev.flexmodel.flow.dto.bo.NodeInstance;
import dev.flexmodel.flow.common.ErrorEnum;

import java.util.List;

public class NodeInstanceListResult extends CommonResult {
  private List<NodeInstance> nodeInstanceList;

  public NodeInstanceListResult(ErrorEnum errorEnum) {
    super(errorEnum);
  }

  public List<NodeInstance> getNodeInstanceList() {
    return nodeInstanceList;
  }

  public void setNodeInstanceList(List<NodeInstance> nodeInstanceList) {
    this.nodeInstanceList = nodeInstanceList;
  }

  @Override
  public String toString() {
    return "NodeInstanceListResult{" +
           "nodeInstanceList=" + nodeInstanceList +
           '}';
  }
}
