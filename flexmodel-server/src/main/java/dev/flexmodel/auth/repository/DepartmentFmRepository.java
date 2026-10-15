package dev.flexmodel.auth.repository;

import dev.flexmodel.codegen.entity.Department;
import dev.flexmodel.session.Session;
import dev.flexmodel.session.SessionFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

import static dev.flexmodel.codegen.System.department;

@ApplicationScoped
public class DepartmentFmRepository implements DepartmentRepository {

  @Inject
  SessionFactory sessionFactory;

  @Override
  public Department findById(String departmentId) {
    try (Session session = sessionFactory.createSession()) {
      return session.dsl()
        .selectFrom(Department.class)
        .where(department.id.eq(departmentId))
        .executeOne();
    }
  }

  @Override
  public List<Department> findAll() {
    try (Session session = sessionFactory.createSession()) {
      return session.dsl()
        .selectFrom(Department.class)
        .execute();
    }
  }

  @Override
  public List<Department> findByParentId(String parentId) {
    try (Session session = sessionFactory.createSession()) {
      return session.dsl()
        .selectFrom(Department.class)
        .where(department.parentId.eq(parentId))
        .execute();
    }
  }

  @Override
  public Department save(Department departmentEntity) {
    try (Session session = sessionFactory.createSession()) {
      session.dsl()
        .mergeInto(Department.class)
        .values(departmentEntity)
        .execute();
    }
    return departmentEntity;
  }

  @Override
  public void delete(String departmentId) {
    try (Session session = sessionFactory.createSession()) {
      session.dsl()
        .deleteFrom(Department.class)
        .where(department.id.eq(departmentId))
        .execute();
    }
  }
}
