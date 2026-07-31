package dev.flexmodel.auth.service;

import dev.flexmodel.auth.dto.RoleRequest;
import dev.flexmodel.auth.dto.RoleResponse;
import dev.flexmodel.auth.repository.ResourceRepository;
import dev.flexmodel.auth.repository.RoleRepository;
import dev.flexmodel.common.NotFoundException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import dev.flexmodel.codegen.entity.Resource;
import dev.flexmodel.codegen.entity.Role;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@ApplicationScoped
public class RoleService {

  @Inject
  RoleRepository roleRepository;
  @Inject
  ResourceRepository resourceRepository;
  @Inject
  ResourceService resourceService;

  public List<Role> findAll() {
    return roleRepository.findAll();
  }

  public Role findById(String roleId) {
    return roleRepository.findById(roleId);
  }

  public Role create(Role role) {
    return roleRepository.save(role);
  }

  public Role update(Role role) {
    Role existingRole = roleRepository.findById(role.getId());
    if (existingRole == null) {
      throw new NotFoundException("Role not found");
    }
    role.setCreatedAt(existingRole.getCreatedAt());
    return roleRepository.save(role);
  }

  public void delete(String roleId) {
    roleRepository.delete(roleId);
  }

  public List<String> findPermissions(List<String> roleIds) {
    List<Role> roles = roleRepository.findByIds(roleIds);
    List<Long> resourceIds = new ArrayList<>();
    for (Role role : roles) {
      resourceIds.addAll(
        Arrays.stream(role.getResourceIds().split(","))
          .filter(resourceId -> !resourceId.isEmpty())
          .map(Long::parseLong)
          .toList()
      );
    }
    return resourceRepository.findPermissions(resourceIds);
  }

  public List<RoleResponse> findAllRoles() {
    List<Resource> allResources = resourceService.findAll();
    return findAll().stream()
      .map(role -> RoleResponse.fromRole(role, allResources))
      .toList();
  }

  public RoleResponse findRoleById(String roleId) {
    Role role = findById(roleId);
    if (role == null) {
      return null;
    }
    List<Resource> allResources = resourceService.findAll();
    return RoleResponse.fromRole(role, allResources);
  }

  public RoleResponse createRole(RoleRequest request) {
    Role role = new Role();
    role.setId(request.getId());
    role.setName(request.getName());
    role.setDescription(request.getDescription());
    role.setResourceIds(String.join(",", request.getResourceIds()));

    Role savedRole = create(role);
    List<Resource> allResources = resourceService.findAll();
    return RoleResponse.fromRole(savedRole, allResources);
  }

  public RoleResponse updateRole(RoleRequest request) {
    Role existingRole = findById(request.getId());
    if (existingRole == null) {
      throw new NotFoundException("Role not found");
    }

    Role role = new Role();
    role.setId(request.getId());
    role.setName(request.getName());
    role.setDescription(request.getDescription());
    role.setResourceIds(String.join(",", request.getResourceIds()));
    role.setCreatedBy(existingRole.getCreatedBy());
    role.setCreatedAt(existingRole.getCreatedAt());

    Role savedRole = update(role);
    List<Resource> allResources = resourceService.findAll();
    return RoleResponse.fromRole(savedRole, allResources);
  }

  public void deleteRole(String roleId) {
    delete(roleId);
  }

}
