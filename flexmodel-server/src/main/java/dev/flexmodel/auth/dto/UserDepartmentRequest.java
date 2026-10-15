package dev.flexmodel.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserDepartmentRequest {

  private String departmentId;
  private Boolean isMain;
  private String jobTitle;
  private String managerUserId;
}
