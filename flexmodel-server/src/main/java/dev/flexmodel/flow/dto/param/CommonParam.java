package dev.flexmodel.flow.dto.param;

public class CommonParam {
  private String projectId;
  private String createdBy;

  public CommonParam(String projectId, String createdBy) {
    this.projectId = projectId;
    this.createdBy = createdBy;
  }

  public String getProjectId() {
    return projectId;
  }

  public void setProjectId(String projectId) {
    this.projectId = projectId;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(String createdBy) {
    this.createdBy = createdBy;
  }

  @Override
  public String toString() {
    return "CommonParam{" +
           "tenant='" + projectId + '\'' +
      ", createdBy='" + createdBy + '\'' +
           '}';
  }
}
