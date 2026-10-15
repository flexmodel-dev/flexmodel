package dev.flexmodel.auth.repository;

import dev.flexmodel.codegen.entity.UserRole;

import java.util.List;

public interface UserRoleRepository {

  List<UserRole> findByUserId(String userId);

  List<UserRole> findByUserIds(List<String> userIds);

  void deleteByUserId(String userId);

  void deleteByRoleId(String roleId);

  UserRole save(UserRole userRole);
}
