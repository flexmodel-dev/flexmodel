package dev.flexmodel.auth.repository;

import dev.flexmodel.codegen.entity.UserRole;
import dev.flexmodel.session.Session;
import dev.flexmodel.session.SessionFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

import static dev.flexmodel.codegen.System.userRole;

@ApplicationScoped
public class UserRoleFmRepository implements UserRoleRepository {

  @Inject
  SessionFactory sessionFactory;

  @Override
  public List<UserRole> findByUserId(String userId) {
    try (Session session = sessionFactory.createSession()) {
      return session.dsl()
        .selectFrom(UserRole.class)
        .where(userRole.userId.eq(userId))
        .execute();
    }
  }

  @Override
  public List<UserRole> findByUserIds(List<String> userIds) {
    if (userIds == null || userIds.isEmpty()) {
      return List.of();
    }
    try (Session session = sessionFactory.createSession()) {
      return session.dsl()
        .selectFrom(UserRole.class)
        .where(userRole.userId.in(userIds))
        .execute();
    }
  }

  @Override
  public void deleteByUserId(String userId) {
    try (Session session = sessionFactory.createSession()) {
      session.dsl()
        .deleteFrom(UserRole.class)
        .where(userRole.userId.eq(userId))
        .execute();
    }
  }

  @Override
  public void deleteByRoleId(String roleId) {
    try (Session session = sessionFactory.createSession()) {
      session.dsl()
        .deleteFrom(UserRole.class)
        .where(userRole.roleId.eq(roleId))
        .execute();
    }
  }

  @Override
  public UserRole save(UserRole userRoleEntity) {
    try (Session session = sessionFactory.createSession()) {
      session.dsl()
        .mergeInto(UserRole.class)
        .values(userRoleEntity)
        .execute();
    }
    return userRoleEntity;
  }
}
