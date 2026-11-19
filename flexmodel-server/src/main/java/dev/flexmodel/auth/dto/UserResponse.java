package dev.flexmodel.auth.dto;

import dev.flexmodel.codegen.entity.Role;
import dev.flexmodel.codegen.entity.User;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Getter
@Setter
public class UserResponse {

  private String id;
  private String name;
  private String email;
  private String avatar;
  private String createdBy;
  private String updatedBy;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private String status;
  private String managerUserId;
  private String managerName;
  private List<RoleSummary> roles = Collections.emptyList();
  private List<UserDepartmentResponse> departments = Collections.emptyList();

  public static UserResponse fromUser(
    User user,
    List<Role> roles,
    List<UserDepartmentResponse> departments,
    User managerUser
  ) {
    UserResponse response = new UserResponse();
    response.setId(user.getId());
    response.setName(user.getName());
    response.setEmail(user.getEmail());
    response.setAvatar(user.getAvatar());
    response.setCreatedBy(user.getCreatedBy());
    response.setUpdatedBy(user.getUpdatedBy());
    response.setCreatedAt(user.getCreatedAt());
    response.setUpdatedAt(user.getUpdatedAt());
    response.setStatus(user.getStatus() == null ? "ACTIVE" : user.getStatus().name());
    response.setManagerUserId(user.getManagerUserId());
    response.setManagerName(managerUser == null ? null : managerUser.getName());
    response.setDepartments(departments);
    response.setRoles(roles == null ? Collections.emptyList() : roles.stream().map(role -> {
      RoleSummary summary = new RoleSummary();
      summary.setId(role.getId());
      summary.setName(role.getName());
      return summary;
    }).toList());
    return response;
  }

  @Getter
  @Setter
  public static class RoleSummary {
    private String id;
    private String name;
  }
}
