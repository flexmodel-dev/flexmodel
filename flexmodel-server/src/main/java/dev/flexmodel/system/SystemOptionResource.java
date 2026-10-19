package dev.flexmodel.system;

import dev.flexmodel.auth.repository.DepartmentRepository;
import dev.flexmodel.auth.repository.RoleRepository;
import dev.flexmodel.auth.repository.UserDepartmentRepository;
import dev.flexmodel.auth.repository.UserRepository;
import dev.flexmodel.codegen.entity.UserDepartment;
import dev.flexmodel.codegen.entity.Department;
import dev.flexmodel.codegen.entity.Role;
import dev.flexmodel.codegen.entity.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "系统表单", description = "系统表单组件选项")
@ApplicationScoped
@Path("/system/options")
@Produces(MediaType.APPLICATION_JSON)
public class SystemOptionResource {

  @Inject
  DepartmentRepository departmentRepository;

  @Inject
  RoleRepository roleRepository;

  @Inject
  UserRepository userRepository;

  @Inject
  UserDepartmentRepository userDepartmentRepository;

  @Operation(summary = "获取部门选项")
  @GET
  @Path("/departments")
  public List<Map<String, Object>> departments() {
    Map<String, Long> counts = userDepartmentRepository
      .findByDepartmentIds(departmentRepository.findAll().stream()
        .map(Department::getId)
        .toList())
      .stream()
      .collect(Collectors.groupingBy(UserDepartment::getDepartmentId, Collectors.counting()));
    return departmentRepository.findAll().stream()
      .map(department -> {
        Map<String, Object> value = valueMap(
          "id", department.getId(),
          "name", department.getName(),
          "parent_id", department.getParentId());
        value.put("direct_user_count", counts.getOrDefault(department.getId(), 0L));
        return value;
      })
      .toList();
  }

  @Operation(summary = "获取角色选项")
  @GET
  @Path("/roles")
  public List<Map<String, Object>> roles() {
    return roleRepository.findAll().stream()
      .map(role -> valueMap("id", role.getId(), "name", role.getName()))
      .toList();
  }

  @Operation(summary = "获取用户选项")
  @GET
  @Path("/users")
  public List<Map<String, Object>> users() {
    return userRepository.findAll().stream()
      .map(user -> valueMap("id", user.getId(), "name", user.getName()))
      .toList();
  }

  private Map<String, Object> valueMap(Object... pairs) {
    Map<String, Object> result = new LinkedHashMap<>();
    for (int index = 0; index < pairs.length; index += 2) {
      result.put(String.valueOf(pairs[index]), pairs[index + 1]);
    }
    return result;
  }

}
