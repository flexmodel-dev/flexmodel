package dev.flexmodel.auth.repository;

import dev.flexmodel.codegen.entity.Department;

import java.util.List;

public interface DepartmentRepository {

  Department findById(String departmentId);

  List<Department> findAll();

  List<Department> findByParentId(String parentId);

  Department save(Department department);

  void delete(String departmentId);
}
