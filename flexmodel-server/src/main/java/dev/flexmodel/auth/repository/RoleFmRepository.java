package dev.flexmodel.auth.repository;

import dev.flexmodel.codegen.entity.Role;
import dev.flexmodel.session.Session;
import dev.flexmodel.session.SessionFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

import static dev.flexmodel.codegen.System.role;

@ApplicationScoped
public class RoleFmRepository implements RoleRepository {

  @Inject
  SessionFactory sessionFactory;

  @Override
  public Role findById(String roleId) {
    try (Session session = sessionFactory.createSession()) {
      return session.dsl()
        .selectFrom(Role.class)
        .where(role.id.eq(roleId))
        .executeOne();
    }
  }

  @Override
  public List<Role> findAll() {
    try (Session session = sessionFactory.createSession()) {
      return session.dsl()
        .selectFrom(Role.class)
        .execute();
    }
  }

  @Override
  public Role save(Role role) {
    try (Session session = sessionFactory.createSession()) {
      session.dsl()
        .mergeInto(Role.class)
        .values(role)
        .execute();
    }
    return role;
  }

  @Override
  public void delete(String roleId) {
    try (Session session = sessionFactory.createSession()) {
      session.dsl()
        .deleteFrom(Role.class)
        .where(role.id.eq(roleId))
        .execute();
    }
  }

  @Override
  public List<Role> findByIds(List<String> roleIds) {
    try (Session session = sessionFactory.createSession()) {
      return session.dsl()
        .selectFrom(Role.class)
        .where(role.id.in(roleIds))
        .execute();
    }
  }
}
