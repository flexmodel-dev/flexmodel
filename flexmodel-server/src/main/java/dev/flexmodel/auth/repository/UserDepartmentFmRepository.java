package dev.flexmodel.auth.repository;

import dev.flexmodel.codegen.entity.UserDepartment;
import dev.flexmodel.session.Session;
import dev.flexmodel.session.SessionFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

import static dev.flexmodel.codegen.System.userDepartment;

@ApplicationScoped
public class UserDepartmentFmRepository implements UserDepartmentRepository {

  @Inject
  SessionFactory sessionFactory;

  @Override
  public List<UserDepartment> findByUserId(String userId) {
    try (Session session = sessionFactory.createSession()) {
      return session.dsl()
        .selectFrom(UserDepartment.class)
        .where(userDepartment.userId.eq(userId))
        .execute();
    }
  }

  @Override
  public List<UserDepartment> findByUserIds(List<String> userIds) {
    if (userIds == null || userIds.isEmpty()) {
      return List.of();
    }
    try (Session session = sessionFactory.createSession()) {
      return session.dsl()
        .selectFrom(UserDepartment.class)
        .where(userDepartment.userId.in(userIds))
        .execute();
    }
  }

  @Override
  public List<UserDepartment> findByDepartmentIds(List<String> departmentIds) {
    if (departmentIds == null || departmentIds.isEmpty()) {
      return List.of();
    }
    try (Session session = sessionFactory.createSession()) {
      return session.dsl()
        .selectFrom(UserDepartment.class)
        .where(userDepartment.departmentId.in(departmentIds))
        .execute();
    }
  }

  @Override
  public void deleteByUserId(String userId) {
    try (Session session = sessionFactory.createSession()) {
      session.dsl()
        .deleteFrom(UserDepartment.class)
        .where(userDepartment.userId.eq(userId))
        .execute();
    }
  }

  @Override
  public UserDepartment save(UserDepartment userDepartmentEntity) {
    try (Session session = sessionFactory.createSession()) {
      session.dsl()
        .mergeInto(UserDepartment.class)
        .values(userDepartmentEntity)
        .execute();
    }
    return userDepartmentEntity;
  }
}
