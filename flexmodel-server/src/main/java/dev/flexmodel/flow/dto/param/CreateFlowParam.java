package dev.flexmodel.flow.dto.param;

public class CreateFlowParam extends OperationParam {
  private String flowKey;
  private String flowName;
  private String remark;

  public CreateFlowParam(String tenant, String createdBy) {
    super(tenant, createdBy);
  }

  public String getFlowKey() {
    return flowKey;
  }

  public void setFlowKey(String flowKey) {
    this.flowKey = flowKey;
  }

  public String getFlowName() {
    return flowName;
  }

  public void setFlowName(String flowName) {
    this.flowName = flowName;
  }

  public String getRemark() {
    return remark;
  }

  public void setRemark(String remark) {
    this.remark = remark;
  }

  @Override
  public String toString() {
    return "CreateFlowParam{" +
           "flowKey='" + flowKey + '\'' +
           ", flowName='" + flowName + '\'' +
           ", remark='" + remark + '\'' +
           '}';
  }
}
