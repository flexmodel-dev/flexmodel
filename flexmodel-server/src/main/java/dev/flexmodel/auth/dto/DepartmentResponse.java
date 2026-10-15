package dev.flexmodel.auth.dto;

import dev.flexmodel.codegen.entity.Department;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class DepartmentResponse {

  private String id;
  private String name;
  private String parentId;
  private String createdBy;
  private String updatedBy;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private long directUserCount;

  public static DepartmentResponse fromDepartment(Department departmentEntity, long directUserCount) {
    DepartmentResponse response = new DepartmentResponse();
    response.setId(departmentEntity.getId());
    response.setName(departmentEntity.getName());
    response.setParentId(departmentEntity.getParentId());
    response.setCreatedBy(departmentEntity.getCreatedBy());
    response.setUpdatedBy(departmentEntity.getUpdatedBy());
    response.setCreatedAt(departmentEntity.getCreatedAt());
    response.setUpdatedAt(departmentEntity.getUpdatedAt());
    response.setDirectUserCount(directUserCount);
    return response;
  }
}
