package dev.flexmodel.flow.dto.param;

public class OperationParam extends CommonParam {
  private String updatedBy;

  public OperationParam(String tenant, String createdBy) {
    super(tenant, createdBy);
  }

  public String getUpdatedBy() {
    return updatedBy;
  }

  public void setUpdatedBy(String updatedBy) {
    this.updatedBy = updatedBy;
  }

  @Override
  public String toString() {
    return "OperationParam{" +
      "updatedBy='" + updatedBy + '\'' +
           '}';
  }
}
