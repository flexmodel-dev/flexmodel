package dev.flexmodel.flow.dto.param;

public class DeployFlowParam extends OperationParam {
  private String flowModuleId;

  public DeployFlowParam(String tenant, String createdBy) {
    super(tenant, createdBy);
  }

  public String getFlowModuleId() {
    return flowModuleId;
  }

  public void setFlowModuleId(String flowModuleId) {
    this.flowModuleId = flowModuleId;
  }

  @Override
  public String toString() {
    return "DeployFlowParam{" +
           "flowModuleId='" + flowModuleId + '\'' +
           '}';
  }
}
