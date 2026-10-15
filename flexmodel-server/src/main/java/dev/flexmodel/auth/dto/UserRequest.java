package dev.flexmodel.auth.dto;

import dev.flexmodel.codegen.enumeration.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserRequest {

  private String id;
  private String name;
  private String email;
  private String password;
  private String managerUserId;
  private List<String> roleIds = new ArrayList<>();
  private UserStatus status;
  private List<UserDepartmentRequest> departments = new ArrayList<>();
  private String createdBy;
  private String updatedBy;
}
