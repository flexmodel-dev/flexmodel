package dev.flexmodel.auth.repository;

import dev.flexmodel.codegen.entity.RoleResource;

import java.util.List;

public interface RoleResourceRepository {

  List<RoleResource> findByRoleIds(List<String> roleIds);

  void deleteByRoleId(String roleId);

  RoleResource save(RoleResource roleResource);
}
