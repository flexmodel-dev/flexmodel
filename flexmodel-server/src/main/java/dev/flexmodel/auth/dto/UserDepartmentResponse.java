package dev.flexmodel.auth.dto;

import dev.flexmodel.codegen.entity.Department;
import dev.flexmodel.codegen.entity.UserDepartment;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserDepartmentResponse {

  private String departmentId;
  private String departmentName;
  private Boolean isMain;
  private String jobTitle;

  public static UserDepartmentResponse fromUserDepartment(
    UserDepartment userDepartment,
    Department departmentEntity
  ) {
    UserDepartmentResponse response = new UserDepartmentResponse();
    response.setDepartmentId(userDepartment.getDepartmentId());
    response.setDepartmentName(departmentEntity != null ? departmentEntity.getName() : null);
    response.setIsMain(Boolean.TRUE.equals(userDepartment.getIsMain()));
    response.setJobTitle(userDepartment.getJobTitle());
    return response;
  }
}
