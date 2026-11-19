package dev.flexmodel.auth.repository;

import dev.flexmodel.codegen.entity.UserDepartment;

import java.util.List;

public interface UserDepartmentRepository {

  List<UserDepartment> findByUserId(String userId);

  List<UserDepartment> findByUserIds(List<String> userIds);

  List<UserDepartment> findByDepartmentIds(List<String> departmentIds);

  void deleteByUserId(String userId);

  UserDepartment save(UserDepartment userDepartment);
}
