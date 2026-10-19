package dev.flexmodel.auth.service;

import dev.flexmodel.auth.repository.ResourceRepository;
import dev.flexmodel.auth.repository.RoleResourceRepository;
import dev.flexmodel.codegen.entity.RoleResource;
import dev.flexmodel.common.ValidationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class RoleResourceService {

  @Inject
  RoleResourceRepository roleResourceRepository;

  @Inject
  ResourceRepository resourceRepository;

  public List<Long> findResourceIdsByRoleIds(List<String> roleIds) {
    return roleResourceRepository.findByRoleIds(roleIds).stream()
      .map(RoleResource::getResourceId)
      .distinct()
      .toList();
  }

  public void replaceRelations(String roleId, List<Long> resourceIds, String updatedBy) {
    if (roleId == null || roleId.isBlank()) {
      throw new ValidationException("角色ID不能为空");
    }
    if (resourceIds == null) {
      resourceIds = List.of();
    }
    Set<Long> distinctResourceIds = new HashSet<>();
    for (Long resourceId : resourceIds) {
      if (resourceId == null) {
        throw new ValidationException("资源ID不能为空");
      }
      if (!distinctResourceIds.add(resourceId)) {
        throw new ValidationException("角色资源关系重复");
      }
      if (resourceRepository.findById(resourceId) == null) {
        throw new ValidationException("资源不存在: " + resourceId);
      }
    }
    roleResourceRepository.deleteByRoleId(roleId);
    for (Long resourceId : distinctResourceIds) {
      RoleResource relation = new RoleResource();
      relation.setId(UUID.randomUUID().toString());
      relation.setRoleId(roleId);
      relation.setResourceId(resourceId);
      relation.setUpdatedBy(updatedBy);
      roleResourceRepository.save(relation);
    }
  }

  public void deleteRelationsByRoleId(String roleId) {
    roleResourceRepository.deleteByRoleId(roleId);
  }
}
