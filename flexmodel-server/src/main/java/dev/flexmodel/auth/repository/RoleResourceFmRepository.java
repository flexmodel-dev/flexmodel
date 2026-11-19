package dev.flexmodel.auth.repository;

import dev.flexmodel.codegen.entity.RoleResource;
import dev.flexmodel.session.Session;
import dev.flexmodel.session.SessionFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

import static dev.flexmodel.codegen.System.roleResource;

@ApplicationScoped
public class RoleResourceFmRepository implements RoleResourceRepository {

  @Inject
  SessionFactory sessionFactory;

  @Override
  public List<RoleResource> findByRoleIds(List<String> roleIds) {
    if (roleIds == null || roleIds.isEmpty()) {
      return List.of();
    }
    try (Session session = sessionFactory.createSession()) {
      return session.dsl()
        .selectFrom(RoleResource.class)
        .where(roleResource.roleId.in(roleIds))
        .execute();
    }
  }

  @Override
  public void deleteByRoleId(String roleId) {
    try (Session session = sessionFactory.createSession()) {
      session.dsl()
        .deleteFrom(RoleResource.class)
        .where(roleResource.roleId.eq(roleId))
        .execute();
    }
  }

  @Override
  public RoleResource save(RoleResource roleResource) {
    try (Session session = sessionFactory.createSession()) {
      session.dsl()
        .mergeInto(RoleResource.class)
        .values(roleResource)
        .execute();
    }
    return roleResource;
  }
}
