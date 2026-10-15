package dev.flexmodel.auth.service;

import dev.flexmodel.auth.dto.UserDepartmentRequest;
import dev.flexmodel.auth.dto.UserDepartmentResponse;
import dev.flexmodel.auth.repository.DepartmentRepository;
import dev.flexmodel.auth.repository.UserDepartmentRepository;
import dev.flexmodel.auth.repository.UserRepository;
import dev.flexmodel.codegen.entity.Department;
import dev.flexmodel.codegen.entity.User;
import dev.flexmodel.codegen.entity.UserDepartment;
import dev.flexmodel.common.NotFoundException;
import dev.flexmodel.common.ValidationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApplicationScoped
public class UserDepartmentService {

  @Inject
  UserDepartmentRepository userDepartmentRepository;
  @Inject
  DepartmentRepository departmentRepository;
  @Inject
  UserRepository userRepository;

  public Map<String, List<UserDepartmentResponse>> findUserDepartmentsByUserIds(List<String> userIds) {
    List<UserDepartment> relations = userDepartmentRepository.findByUserIds(userIds);
    if (relations.isEmpty()) {
      return Map.of();
    }
    Map<String, Department> departments = departmentRepository.findAll().stream()
      .collect(Collectors.toMap(Department::getId, Function.identity()));
    Map<String, User> users = userRepository.findAll().stream()
      .collect(Collectors.toMap(User::getId, Function.identity()));
    return relations.stream()
      .collect(Collectors.groupingBy(
        UserDepartment::getUserId,
        Collectors.mapping(
          relation -> UserDepartmentResponse.fromUserDepartment(
            relation,
            departments.get(relation.getDepartmentId()),
            users.get(relation.getManagerUserId())
          ),
          Collectors.toList()
        )
      ));
  }

  public void replaceRelations(String userId, List<UserDepartmentRequest> requests, String updatedBy) {
    if (userId == null || userId.isBlank()) {
      throw new ValidationException("User id is required");
    }
    if (requests == null) {
      requests = List.of();
    }
    validateRelations(userId, requests);

    userDepartmentRepository.deleteByUserId(userId);
    for (UserDepartmentRequest request : requests) {
      UserDepartment relation = new UserDepartment();
      relation.setId(UUID.randomUUID().toString());
      relation.setUserId(userId);
      relation.setDepartmentId(request.getDepartmentId());
      relation.setIsMain(Boolean.TRUE.equals(request.getIsMain()));
      relation.setJobTitle(request.getJobTitle());
      relation.setManagerUserId(request.getManagerUserId());
      relation.setUpdatedBy(updatedBy);
      userDepartmentRepository.save(relation);
    }
  }

  public void deleteRelations(String userId) {
    userDepartmentRepository.deleteByUserId(userId);
  }

  private void validateRelations(String userId, List<UserDepartmentRequest> requests) {
    Set<String> departmentIds = new HashSet<>();
    long mainCount = 0;
    for (UserDepartmentRequest request : requests) {
      if (request == null || request.getDepartmentId() == null || request.getDepartmentId().isBlank()) {
        throw new ValidationException("Department id is required");
      }
      if (!departmentIds.add(request.getDepartmentId())) {
        throw new ValidationException("Duplicate user department relation");
      }
      if (departmentRepository.findById(request.getDepartmentId()) == null) {
        throw new NotFoundException("Department not found");
      }
      if (request.getManagerUserId() != null && !request.getManagerUserId().isBlank()) {
        if (request.getManagerUserId().equals(userId)) {
          throw new ValidationException("User cannot be their own manager");
        }
        if (userRepository.findById(request.getManagerUserId()) == null) {
          throw new NotFoundException("Manager user not found");
        }
      }
      if (Boolean.TRUE.equals(request.getIsMain())) {
        mainCount++;
      }
    }
    if (!requests.isEmpty() && mainCount != 1) {
      throw new ValidationException("User departments require exactly one main department");
    }
  }
}
