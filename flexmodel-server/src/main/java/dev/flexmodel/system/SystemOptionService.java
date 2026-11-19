package dev.flexmodel.system;

import dev.flexmodel.auth.repository.DepartmentRepository;
import dev.flexmodel.auth.repository.RoleRepository;
import dev.flexmodel.auth.repository.UserDepartmentRepository;
import dev.flexmodel.auth.repository.UserRepository;
import dev.flexmodel.codegen.entity.UserDepartment;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 系统表单组件选项服务。
 *
 * @author cjbi
 */
@ApplicationScoped
public class SystemOptionService {

  @Inject
  DepartmentRepository departmentRepository;

  @Inject
  RoleRepository roleRepository;

  @Inject
  UserRepository userRepository;

  @Inject
  UserDepartmentRepository userDepartmentRepository;

  public List<Map<String, Object>> departments() {
    Map<String, Long> counts = userDepartmentRepository
      .findByDepartmentIds(departmentRepository.findAll().stream()
        .map(department -> department.getId())
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

  public List<Map<String, Object>> roles() {
    return roleRepository.findAll().stream()
      .map(role -> valueMap("id", role.getId(), "name", role.getName()))
      .toList();
  }

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
