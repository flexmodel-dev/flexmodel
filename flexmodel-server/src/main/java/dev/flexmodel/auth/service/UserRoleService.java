package dev.flexmodel.auth.service;

import dev.flexmodel.auth.repository.UserRoleRepository;
import dev.flexmodel.auth.repository.RoleRepository;
import dev.flexmodel.codegen.entity.UserRole;
import dev.flexmodel.common.ValidationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class UserRoleService {

  @Inject
  UserRoleRepository userRoleRepository;
  @Inject
  RoleRepository roleRepository;

  public Map<String, List<String>> findRoleIdsByUserIds(List<String> userIds) {
    return userRoleRepository.findByUserIds(userIds).stream()
      .collect(Collectors.groupingBy(
        UserRole::getUserId,
        Collectors.mapping(UserRole::getRoleId, Collectors.toList())
      ));
  }

  public List<String> findRoleIdsByUserId(String userId) {
    return userRoleRepository.findByUserId(userId).stream()
      .map(UserRole::getRoleId)
      .toList();
  }

  public void replaceRelations(String userId, List<String> roleIds, String updatedBy) {
    if (userId == null || userId.isBlank()) {
      throw new ValidationException("User id is required");
    }
    if (roleIds == null) {
      roleIds = List.of();
    }
    Set<String> distinctRoleIds = new HashSet<>();
    for (String roleId : roleIds) {
      if (roleId == null || roleId.isBlank()) {
        throw new ValidationException("Role id is required");
      }
      if (!distinctRoleIds.add(roleId)) {
        throw new ValidationException("Duplicate user role relation");
      }
      if (roleRepository.findById(roleId) == null) {
        throw new ValidationException("Role not found: " + roleId);
      }
    }

    userRoleRepository.deleteByUserId(userId);
    for (String roleId : distinctRoleIds) {
      UserRole relation = new UserRole();
      relation.setId(UUID.randomUUID().toString());
      relation.setUserId(userId);
      relation.setRoleId(roleId);
      relation.setUpdatedBy(updatedBy);
      userRoleRepository.save(relation);
    }
  }

  public void deleteRelations(String userId) {
    userRoleRepository.deleteByUserId(userId);
  }

  public void deleteRelationsByRoleId(String roleId) {
    userRoleRepository.deleteByRoleId(roleId);
  }
}
