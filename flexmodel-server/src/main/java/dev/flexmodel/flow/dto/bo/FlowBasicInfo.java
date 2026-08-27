package dev.flexmodel.flow.dto.bo;

public class FlowBasicInfo {
  private String flowDeployId;
  private String flowModuleId;
  private String initiator;

  public String getFlowDeployId() {
    return flowDeployId;
  }

  public void setFlowDeployId(String flowDeployId) {
    this.flowDeployId = flowDeployId;
  }

  public String getFlowModuleId() {
    return flowModuleId;
  }

  public void setFlowModuleId(String flowModuleId) {
    this.flowModuleId = flowModuleId;
  }

  public String getInitiator() {
    return initiator;
  }

  public void setInitiator(String initiator) {
    this.initiator = initiator;
  }

  @Override
  public String toString() {
    return "FlowBasicInfo{" +
           "flowDeployId='" + flowDeployId + '\'' +
      ", flowModuleId='" + flowModuleId + '\'' +
      ", initiator='" + initiator + '\'' +
           '}';
  }
}
