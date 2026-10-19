package dev.flexmodel.system;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.auth.dto.UserDepartmentRequest;
import dev.flexmodel.auth.repository.DepartmentRepository;
import dev.flexmodel.auth.repository.ResourceRepository;
import dev.flexmodel.auth.repository.RoleRepository;
import dev.flexmodel.auth.repository.UserRepository;
import dev.flexmodel.auth.repository.UserDepartmentRepository;
import dev.flexmodel.auth.service.RoleResourceService;
import dev.flexmodel.auth.service.SecurityUtil;
import dev.flexmodel.auth.service.UserDepartmentService;
import dev.flexmodel.auth.service.UserRoleService;
import dev.flexmodel.codegen.entity.Department;
import dev.flexmodel.codegen.entity.Resource;
import dev.flexmodel.codegen.entity.Role;
import dev.flexmodel.codegen.entity.UserDepartment;
import dev.flexmodel.common.SessionContext;
import dev.flexmodel.common.ValidationException;
import dev.flexmodel.common.config.EngineConfig;
import dev.flexmodel.common.dto.PageDTO;
import dev.flexmodel.common.InternalServerException;
import dev.flexmodel.common.NotFoundException;
import dev.flexmodel.data.DataService;
import dev.flexmodel.system.dto.SystemFormResponse;
import dev.flexmodel.system.dto.SystemFormFieldRecord;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 系统表单记录服务，保持扁平 JSON 契约。
 */
@ApplicationScoped
public class SystemRecordService {

  private static final String SYSTEM_SCHEMA = EngineConfig.SYSTEM_DS_KEY;
  private static final String SYSTEM_PROJECT_ID = "default";
  private static final int MAX_SIZE = 200;
  private static final Set<String> AUDIT_FIELDS = Set.of(
    "id", "created_by", "updated_by", "created_at", "updated_at");

  @Inject
  SystemFormService systemFormService;

  @Inject
  DataService dataService;

  @Inject
  SessionContext sessionContext;

  @Inject
  UserRoleService userRoleService;

  @Inject
  UserDepartmentService userDepartmentService;

  @Inject
  UserDepartmentRepository userDepartmentRepository;

  @Inject
  UserRepository userRepository;

  @Inject
  RoleRepository roleRepository;

  @Inject
  RoleResourceService roleResourceService;

  @Inject
  ResourceRepository resourceRepository;

  @Inject
  DepartmentRepository departmentRepository;

  public PageDTO<Map<String, Object>> findRecords(
    String formName,
    int page,
    int size,
    String search,
    String sortField,
    String sortOrder,
    String departmentId,
    boolean includeDescendants
  ) {
    SystemFormResponse form = systemFormService.findForm(formName);
    int normalizedPage = Math.max(page, 1);
    int normalizedSize = Math.clamp(size, 1, MAX_SIZE);
    if (!isBlank(departmentId) && !SystemFormService.USER_FORM.equals(formName)) {
      throw new ValidationException("部门筛选仅支持用户表单");
    }

    Map<String, Object> filterConditions = new LinkedHashMap<>();
    List<Map<String, Object>> searchConditions = searchConditions(formName, search);
    if (!searchConditions.isEmpty()) {
      filterConditions.put("_or", searchConditions);
    }
    if (!isBlank(departmentId)) {
      List<String> userIds = userIdsByDepartment(departmentId, includeDescendants);
      if (userIds.isEmpty()) {
        return new PageDTO<>(List.of(), 0L);
      }
      filterConditions.put("id", Map.of("_in", userIds));
    }
    String filter = filterConditions.isEmpty() ? null : JsonUtils.toJsonString(filterConditions);
    String sort = sort(form, sortField, sortOrder);
    PageDTO<Map<String, Object>> result = dataService.findPagingRecords(
      SYSTEM_PROJECT_ID, SYSTEM_SCHEMA, formName, normalizedPage, normalizedSize,
      filter, sort, null);
    List<Map<String, Object>> records = result.list().stream()
      .map(record -> decorate(formName, record))
      .toList();
    return new PageDTO<>(records, result.total());
  }

  public Map<String, Object> findRecord(String formName, String recordId) {
    systemFormService.findForm(formName);
    return decorate(formName, requireRecord(formName, recordId));
  }

  public Map<String, Object> createRecord(String formName, Map<String, Object> input) {
    SystemFormResponse form = systemFormService.findForm(formName);
    Map<String, Object> values = sanitize(form, input, true);
    String recordId = UUID.randomUUID().toString();
    values.put("id", recordId);
    values.put("created_by", currentUser());
    values.put("updated_by", currentUser());
    values.put("created_at", LocalDateTime.now());
    values.put("updated_at", LocalDateTime.now());
    prepareCreate(formName, recordId, values, input);
    dataService.createRecord(SYSTEM_PROJECT_ID, SYSTEM_SCHEMA, formName, values);
    replaceRelations(formName, recordId, input);
    return findRecord(formName, recordId);
  }

  public Map<String, Object> updateRecord(String formName, String recordId, Map<String, Object> input) {
    SystemFormResponse form = systemFormService.findForm(formName);
    Map<String, Object> existing = requireRecord(formName, recordId);
    Map<String, Object> values = sanitize(form, input, false);
    prepareUpdate(formName, recordId, existing, values, input);
    if (!values.isEmpty()) {
      values.put("updated_by", currentUser());
      values.put("updated_at", LocalDateTime.now());
      dataService.updateRecord(SYSTEM_PROJECT_ID, SYSTEM_SCHEMA, formName, recordId, values);
    }
    replaceRelations(formName, recordId, input);
    return findRecord(formName, recordId);
  }

  public void deleteRecord(String formName, String recordId) {
    systemFormService.findForm(formName);
    Map<String, Object> existing = requireRecord(formName, recordId);
    if (SystemFormService.USER_FORM.equals(formName) && recordId.equals(currentUser())) {
      throw new ValidationException("当前用户不能删除自己");
    }
    if (SystemFormService.DEPARTMENT_FORM.equals(formName)) {
      deleteDepartment(recordId, existing);
    }
    if (SystemFormService.USER_FORM.equals(formName)) {
      userRoleService.deleteRelations(recordId);
      userDepartmentService.deleteRelations(recordId);
    }
    if (SystemFormService.ROLE_FORM.equals(formName)) {
      userRoleService.deleteRelationsByRoleId(recordId);
      roleResourceService.deleteRelationsByRoleId(recordId);
    }
    dataService.deleteRecord(SYSTEM_PROJECT_ID, SYSTEM_SCHEMA, formName, recordId);
  }

  private Map<String, Object> sanitize(
    SystemFormResponse form,
    Map<String, Object> input,
    boolean creating
  ) {
    if (input == null) {
      throw new ValidationException("记录数据不能为空");
    }
    Set<String> relationFields = switch (form.name()) {
      case SystemFormService.USER_FORM -> Set.of("roleIds", "departments");
      case SystemFormService.ROLE_FORM -> Set.of("resourceIds");
      default -> Set.of();
    };
    Map<String, SystemFormFieldRecord> fields = form.fields().stream()
      .collect(Collectors.toMap(SystemFormFieldRecord::name, Function.identity(),
        (left, right) -> right, LinkedHashMap::new));
    Map<String, Object> values = new LinkedHashMap<>();
    input.forEach((name, value) -> {
      if (relationFields.contains(name)) {
        return;
      }
      if (AUDIT_FIELDS.contains(name)) {
        throw new ValidationException("系统字段不允许写入: " + name);
      }
      SystemFormFieldRecord field = fields.get(name);
      if (field == null || field.locked() && field.virtual() && !"password".equals(name)) {
        throw new ValidationException("记录字段不存在: " + name);
      }
      if (field.readonly()) {
        throw new ValidationException("只读字段不允许写入: " + name);
      }
      if (creating && field.required() && isEmpty(value) && !"password".equals(name)) {
        throw new ValidationException("必填字段不能为空: " + field.label());
      }
      values.put(name, value);
    });
    if (creating) {
      fields.values().forEach(field -> {
        if (field.required() && !field.virtual() && !field.readonly()
          && isEmpty(values.get(field.name()))) {
          throw new ValidationException("必填字段不能为空: " + field.label());
        }
      });
    }
    return values;
  }

  private void prepareCreate(
    String formName,
    String recordId,
    Map<String, Object> values,
    Map<String, Object> input
  ) {
    if (SystemFormService.USER_FORM.equals(formName)) {
      String status = String.valueOf(values.getOrDefault("status", "ACTIVE"));
      String password = String.valueOf(values.getOrDefault("password", ""));
      if (("ACTIVE".equals(status) || "DISABLED".equals(status)) && password.isBlank()) {
        throw new ValidationException("启用或停用用户必须设置密码");
      }
      values.put("password_hash", passwordHash(recordId, password));
      values.remove("password");
      validateManager(recordId, values.get("manager_user_id"));
    }
    if (SystemFormService.DEPARTMENT_FORM.equals(formName)) {
      validateDepartmentParent(null, stringValue(values.get("parent_id")));
    }
  }

  private void prepareUpdate(
    String formName,
    String recordId,
    Map<String, Object> existing,
    Map<String, Object> values,
    Map<String, Object> input
  ) {
    if (SystemFormService.USER_FORM.equals(formName)) {
      if (values.containsKey("status")
        && "DISABLED".equals(String.valueOf(values.get("status")))
        && recordId.equals(currentUser())) {
        throw new ValidationException("当前用户不能停用自己");
      }
      if (values.containsKey("manager_user_id")) {
        validateManager(recordId, values.get("manager_user_id"));
      }
      if (values.containsKey("password")) {
        String password = String.valueOf(values.get("password"));
        if (!password.isBlank()) {
          values.put("password_hash", passwordHash(recordId, password));
        }
        values.remove("password");
      }
      if (values.containsKey("status") && "ACTIVE".equals(String.valueOf(values.get("status")))) {
        String oldStatus = String.valueOf(existing.get("status"));
        String passwordHash = String.valueOf(existing.get("password_hash"));
        boolean passwordProvided = input.containsKey("password")
          && !isEmpty(input.get("password"));
        if ("INVITED".equals(oldStatus) && (passwordHash.isBlank()) && !passwordProvided) {
          throw new ValidationException("激活待激活用户前必须设置密码");
        }
      }
    }
    if (SystemFormService.DEPARTMENT_FORM.equals(formName) && values.containsKey("parent_id")) {
      validateDepartmentParent(recordId, stringValue(values.get("parent_id")));
    }
  }

  private void replaceRelations(String formName, String recordId, Map<String, Object> input) {
    if (SystemFormService.USER_FORM.equals(formName)) {
      if (input.containsKey("roleIds")) {
        userRoleService.replaceRelations(recordId, stringList(input.get("roleIds")), currentUser());
      }
      if (input.containsKey("departments")) {
        userDepartmentService.replaceRelations(
          recordId, departmentRelations(input.get("departments")), currentUser());
      }
    }
    if (SystemFormService.ROLE_FORM.equals(formName) && input.containsKey("resourceIds")) {
      roleResourceService.replaceRelations(
        recordId, longList(input.get("resourceIds")), currentUser());
    }
  }

  private Map<String, Object> decorate(String formName, Map<String, Object> record) {
    Map<String, Object> result = new LinkedHashMap<>(record);
    if (SystemFormService.USER_FORM.equals(formName)) {
      List<String> roleIds = userRoleService.findRoleIdsByUserId(String.valueOf(record.get("id")));
      result.put("roleIds", roleIds);
      Map<String, Role> roles = roleRepository.findAll().stream()
        .collect(Collectors.toMap(Role::getId, Function.identity()));
      result.put("roles", roleIds.stream()
        .map(roles::get)
        .filter(role -> role != null)
        .map(role -> Map.<String, Object>of(
          "id", role.getId(),
          "name", role.getName()))
        .toList());
      result.put("departments", userDepartmentService
        .findUserDepartmentsByUserIds(List.of(String.valueOf(record.get("id"))))
        .getOrDefault(String.valueOf(record.get("id")), List.of())
        .stream()
        .map(relation -> Map.<String, Object>of(
          "departmentId", relation.getDepartmentId(),
          "departmentName", relation.getDepartmentName(),
          "isMain", relation.getIsMain(),
          "jobTitle", relation.getJobTitle()))
        .toList());
      result.remove("password_hash");
      String managerId = stringValue(record.get("manager_user_id"));
      if (managerId != null) {
        dev.flexmodel.codegen.entity.User manager = userRepository.findById(managerId);
        result.put("manager_name", manager == null ? null : manager.getName());
      } else {
        result.put("manager_name", null);
      }
    }
    if (SystemFormService.DEPARTMENT_FORM.equals(formName)) {
      String departmentId = String.valueOf(record.get("id"));
      long count = userDepartmentRepository.findByDepartmentIds(List.of(departmentId)).stream()
        .map(UserDepartment::getUserId)
        .distinct()
        .count();
      result.put("direct_user_count", count);
    }
    if (SystemFormService.ROLE_FORM.equals(formName)) {
      List<Long> resourceIds = roleResourceService.findResourceIdsByRoleIds(
        List.of(String.valueOf(record.get("id"))));
      result.put("resourceIds", resourceIds);
      Map<Long, Resource> resources = resourceRepository.findAll().stream()
        .collect(Collectors.toMap(Resource::getId, Function.identity()));
      result.put("resources", resourceIds.stream()
        .map(resources::get)
        .filter(resource -> resource != null)
        .map(resource -> {
          Map<String, Object> value = new LinkedHashMap<>();
          value.put("id", resource.getId());
          value.put("name", resource.getName());
          value.put("permission", resource.getPermission());
          value.put("parent_id", resource.getParentId());
          return value;
        })
        .toList());
    }
    return result;
  }

  private Map<String, Object> requireRecord(String formName, String recordId) {
    Map<String, Object> record = dataService.findOneRecord(
      SYSTEM_PROJECT_ID, SYSTEM_SCHEMA, formName, recordId, null);
    if (record == null) {
      throw new NotFoundException("记录不存在: " + recordId);
    }
    return record;
  }

  private List<Map<String, Object>> searchConditions(String formName, String search) {
    if (isBlank(search)) {
      return List.of();
    }
    String keyword = search.trim();
    if (keyword.length() > 100) {
      throw new ValidationException("搜索关键字长度不能超过100");
    }
    List<String> searchFields = switch (formName) {
      case SystemFormService.USER_FORM -> List.of("name", "email");
      case SystemFormService.DEPARTMENT_FORM, SystemFormService.ROLE_FORM -> List.of("name");
      default -> List.of();
    };
    if (searchFields.isEmpty()) {
      return List.of();
    }
    return searchFields.stream()
      .map(field -> Map.<String, Object>of(field, Map.of("_contains", keyword)))
      .toList();
  }

  private List<String> userIdsByDepartment(String departmentId, boolean includeDescendants) {
    Department department = departmentRepository.findById(departmentId);
    if (department == null) {
      throw new NotFoundException("部门不存在: " + departmentId);
    }
    List<String> departmentIds = includeDescendants
      ? descendantDepartmentIds(departmentId)
      : List.of(departmentId);
    return userDepartmentRepository.findByDepartmentIds(departmentIds).stream()
      .map(UserDepartment::getUserId)
      .distinct()
      .toList();
  }

  private List<String> descendantDepartmentIds(String rootDepartmentId) {
    Map<String, List<Department>> children = new HashMap<>();
    for (Department department : departmentRepository.findAll()) {
      children.computeIfAbsent(department.getParentId(), key -> new ArrayList<>()).add(department);
    }
    Set<String> result = new LinkedHashSet<>();
    collectDepartmentIds(rootDepartmentId, children, result);
    return List.copyOf(result);
  }

  private void collectDepartmentIds(
    String departmentId,
    Map<String, List<Department>> children,
    Set<String> result
  ) {
    if (!result.add(departmentId)) {
      return;
    }
    for (Department child : children.getOrDefault(departmentId, List.of())) {
      collectDepartmentIds(child.getId(), children, result);
    }
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private String sort(SystemFormResponse form, String sortField, String sortOrder) {
    String order = normalizeSortOrder(sortOrder);
    String field = sortField == null || sortField.isBlank() ? "created_at" : sortField.trim();
    if (!AUDIT_FIELDS.contains(field)
      && form.fields().stream().noneMatch(item -> item.name().equals(field))) {
      throw new ValidationException("排序字段不存在: " + field);
    }
    return JsonUtils.toJsonString(List.of(Map.of("field", field, "order", order)));
  }

  private String normalizeSortOrder(String sortOrder) {
    if (sortOrder == null || sortOrder.isBlank()) {
      return "DESC";
    }
    return switch (sortOrder.trim().toUpperCase()) {
      case "ASC" -> "ASC";
      case "DESC" -> "DESC";
      default -> throw new ValidationException("排序方向不合法: " + sortOrder);
    };
  }

  private void validateManager(String userId, Object managerUserId) {
    String managerId = stringValue(managerUserId);
    if (managerId == null) {
      return;
    }
    if (managerId.equals(userId)) {
      throw new ValidationException("用户不能作为自己的直属主管");
    }
    if (dataService.findOneRecord(
      SYSTEM_PROJECT_ID, SYSTEM_SCHEMA, SystemFormService.USER_FORM, managerId, null) == null) {
      throw new NotFoundException("直属主管不存在: " + managerId);
    }
  }

  private void validateDepartmentParent(String departmentId, String parentId) {
    if (parentId == null) {
      return;
    }
    if (parentId.equals(departmentId)) {
      throw new ValidationException("部门不能作为自己的上级部门");
    }
    Map<String, Department> departments = departmentRepository.findAll().stream()
      .collect(Collectors.toMap(Department::getId, Function.identity()));
    Department current = departments.get(parentId);
    Set<String> visited = new HashSet<>();
    while (current != null && current.getParentId() != null) {
      if (!visited.add(current.getId())) {
        throw new ValidationException("部门上级关系成环");
      }
      if (departmentId != null && departmentId.equals(current.getParentId())) {
        throw new ValidationException("部门上级关系成环");
      }
      current = departments.get(current.getParentId());
    }
  }

  private void deleteDepartment(String departmentId, Map<String, Object> existing) {
    if (!departmentRepository.findByParentId(departmentId).isEmpty()) {
      throw new ValidationException("部门存在下级部门，不能删除");
    }
    if (!userDepartmentRepository.findByDepartmentIds(List.of(departmentId)).isEmpty()) {
      throw new ValidationException("部门存在成员，不能删除");
    }
  }

  private String passwordHash(String userId, String password) {
    if (password == null || password.isBlank()) {
      return "";
    }
    try {
      return SecurityUtil.md5(userId, password);
    } catch (Exception exception) {
      throw new InternalServerException("密码加密失败", exception);
    }
  }

  private List<String> stringList(Object value) {
    if (!(value instanceof List<?> values)) {
      throw new ValidationException("关联ID必须是数组");
    }
    return values.stream()
      .map(item -> stringValue(item))
      .collect(Collectors.toCollection(LinkedHashSet::new))
      .stream()
      .toList();
  }

  private List<Long> longList(Object value) {
    if (!(value instanceof List<?> values)) {
      throw new ValidationException("关联ID必须是数组");
    }
    LinkedHashSet<Long> result = new LinkedHashSet<>();
    for (Object item : values) {
      if (item instanceof Number number) {
        result.add(number.longValue());
      } else {
        try {
          result.add(Long.parseLong(String.valueOf(item)));
        } catch (NumberFormatException exception) {
          throw new ValidationException("资源ID不合法: " + item);
        }
      }
    }
    return List.copyOf(result);
  }

  private List<UserDepartmentRequest> departmentRelations(Object value) {
    if (!(value instanceof List<?> values)) {
      throw new ValidationException("任职关系必须是数组");
    }
    return values.stream()
      .map(item -> {
        if (!(item instanceof Map<?, ?> map)) {
          throw new ValidationException("任职关系必须是对象");
        }
        UserDepartmentRequest request = new UserDepartmentRequest();
        request.setDepartmentId(stringValue(map.get("departmentId")));
        request.setIsMain(Boolean.parseBoolean(String.valueOf(map.get("isMain"))));
        request.setJobTitle(stringValue(map.get("jobTitle")));
        return request;
      })
      .toList();
  }

  private String stringValue(Object value) {
    if (value == null) {
      return null;
    }
    String text = String.valueOf(value);
    return text.isBlank() ? null : text;
  }

  private String currentUser() {
    String userId = sessionContext.getUserId();
    return userId == null || userId.isBlank() ? "admin" : userId;
  }

  private boolean isEmpty(Object value) {
    if (value == null) {
      return true;
    }
    return value instanceof String text && text.isBlank();
  }
}
