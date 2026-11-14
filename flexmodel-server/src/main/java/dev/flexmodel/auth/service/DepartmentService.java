package dev.flexmodel.auth.service;

import dev.flexmodel.auth.dto.DepartmentRequest;
import dev.flexmodel.auth.dto.DepartmentResponse;
import dev.flexmodel.auth.repository.DepartmentRepository;
import dev.flexmodel.auth.repository.UserDepartmentRepository;
import dev.flexmodel.codegen.entity.Department;
import dev.flexmodel.common.NotFoundException;
import dev.flexmodel.common.ValidationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class DepartmentService {

  @Inject
  DepartmentRepository departmentRepository;
  @Inject
  UserDepartmentRepository userDepartmentRepository;

  public List<DepartmentResponse> findAllDepartments() {
    List<Department> departments = departmentRepository.findAll();
    Map<String, Long> directCounts = userDepartmentRepository
      .findByDepartmentIds(departments.stream().map(Department::getId).toList())
      .stream()
      .collect(Collectors.groupingBy(
        userDepartment -> userDepartment.getDepartmentId(),
        Collectors.counting()
      ));
    return departments.stream()
      .map(department -> DepartmentResponse.fromDepartment(
        department,
        directCounts.getOrDefault(department.getId(), 0L)
      ))
      .toList();
  }

  public List<String> findSelfAndDescendantIds(String departmentId) {
    Map<String, List<Department>> childrenByParent = departmentRepository.findAll().stream()
      .collect(Collectors.groupingBy(department ->
        department.getParentId() == null ? "" : department.getParentId()
      ));
    List<String> result = new ArrayList<>();
    Set<String> visited = new HashSet<>();
    collectSelfAndDescendantIds(departmentId, childrenByParent, result, visited);
    return result;
  }

  public Department findDepartmentById(String departmentId) {
    return departmentRepository.findById(departmentId);
  }

  public DepartmentResponse createDepartment(DepartmentRequest request) {
    validateName(request.getName());
    validateParent(request.getParentId(), null);

    Department department = new Department();
    department.setId(request.getId());
    department.setName(request.getName());
    department.setParentId(request.getParentId());
    department.setCreatedBy(request.getCreatedBy());
    department.setUpdatedBy(request.getUpdatedBy());
    return DepartmentResponse.fromDepartment(departmentRepository.save(department), 0L);
  }

  public DepartmentResponse updateDepartment(String departmentId, DepartmentRequest request) {
    Department existing = departmentRepository.findById(departmentId);
    if (existing == null) {
      throw new NotFoundException("Department not found");
    }
    validateName(request.getName());
    validateParent(request.getParentId(), departmentId);

    Department department = new Department();
    department.setId(departmentId);
    department.setName(request.getName());
    department.setParentId(request.getParentId());
    department.setCreatedBy(existing.getCreatedBy());
    department.setUpdatedBy(request.getUpdatedBy());
    department.setCreatedAt(existing.getCreatedAt());
    departmentRepository.save(department);
    return findDepartmentResponseById(departmentId);
  }

  public void deleteDepartment(String departmentId) {
    Department existing = departmentRepository.findById(departmentId);
    if (existing == null) {
      throw new NotFoundException("Department not found");
    }
    if (!departmentRepository.findByParentId(departmentId).isEmpty()) {
      throw new ValidationException("Department has child departments");
    }
    if (!userDepartmentRepository.findByDepartmentIds(List.of(departmentId)).isEmpty()) {
      throw new ValidationException("Department has members");
    }
    departmentRepository.delete(departmentId);
  }

  public DepartmentResponse findDepartmentResponseById(String departmentId) {
    Department department = departmentRepository.findById(departmentId);
    if (department == null) {
      throw new NotFoundException("Department not found");
    }
    long count = userDepartmentRepository.findByDepartmentIds(List.of(departmentId)).stream()
      .map(userDepartment -> userDepartment.getUserId())
      .distinct()
      .count();
    return DepartmentResponse.fromDepartment(department, count);
  }

  private void collectSelfAndDescendantIds(
    String departmentId,
    Map<String, List<Department>> childrenByParent,
    List<String> result,
    Set<String> visited
  ) {
    if (departmentId == null || visited.contains(departmentId)) {
      return;
    }
    visited.add(departmentId);
    result.add(departmentId);
    for (Department child : childrenByParent.getOrDefault(departmentId, List.of())) {
      collectSelfAndDescendantIds(child.getId(), childrenByParent, result, visited);
    }
  }

  private void validateName(String name) {
    if (name == null || name.isBlank()) {
      throw new ValidationException("Department name is required");
    }
  }

  private void validateParent(String parentId, String selfId) {
    if (parentId == null || parentId.isBlank()) {
      return;
    }
    if (parentId.equals(selfId)) {
      throw new ValidationException("Department cannot be its own parent");
    }
    Department parent = departmentRepository.findById(parentId);
    if (parent == null) {
      throw new NotFoundException("Parent department not found");
    }

    String current = parent.getParentId();
    while (current != null && !current.isBlank()) {
      if (current.equals(selfId)) {
        throw new ValidationException("Department parent forms a cycle");
      }
      current = departmentRepository.findById(current).getParentId();
    }
  }
}
