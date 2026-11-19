package dev.flexmodel.auth.service;

import dev.flexmodel.auth.repository.ResourceRepository;
import dev.flexmodel.auth.repository.RoleRepository;
import dev.flexmodel.codegen.entity.Role;
import dev.flexmodel.common.NotFoundException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class RoleService {

  @Inject
  RoleRepository roleRepository;
  @Inject
  ResourceRepository resourceRepository;
  @Inject
  UserRoleService userRoleService;
  @Inject
  RoleResourceService roleResourceService;

  public List<Role> findAll() {
    return roleRepository.findAll();
  }

  public Role findById(String roleId) {
    return roleRepository.findById(roleId);
  }

  public Role save(Role role) {
    return roleRepository.save(role);
  }

  public void delete(String roleId) {
    roleRepository.delete(roleId);
  }

  public List<String> findPermissions(List<String> roleIds) {
    return resourceRepository.findPermissions(
      roleResourceService.findResourceIdsByRoleIds(roleIds));
  }

  public void deleteRole(String roleId) {
    if (roleRepository.findById(roleId) == null) {
      throw new NotFoundException("角色不存在: " + roleId);
    }
    userRoleService.deleteRelationsByRoleId(roleId);
    roleResourceService.deleteRelationsByRoleId(roleId);
    delete(roleId);
  }
}
