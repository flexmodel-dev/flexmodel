package dev.flexmodel.auth.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import dev.flexmodel.codegen.entity.Resource;
import dev.flexmodel.session.Session;
import dev.flexmodel.session.SessionFactory;

import java.util.List;

import static dev.flexmodel.query.Expressions.field;

@ApplicationScoped
public class ResourceFmRepository implements ResourceRepository {

  @Inject
  SessionFactory sessionFactory;

  @Override
  public Resource findById(Long resourceId) {
    try (Session session = sessionFactory.createSession()) {
      return session.dsl()
        .selectFrom(Resource.class)
        .where(field(Resource::getId).eq(resourceId))
        .executeOne();
    }
  }

  @Override
  public List<Resource> findAll() {
    try (Session session = sessionFactory.createSession()) {
      return session.dsl()
        .selectFrom(Resource.class)
        .execute();
    }
  }

  @Override
  public Resource save(Resource resource) {
    try (Session session = sessionFactory.createSession()) {
      session.dsl()
        .mergeInto(Resource.class)
        .values(resource)
        .execute();
    }
    return resource;
  }

  @Override
  public void delete(Long resourceId) {
    try (Session session = sessionFactory.createSession()) {
      session.dsl()
        .deleteFrom(Resource.class)
        .where(field(Resource::getId).eq(resourceId))
        .execute();
    }
  }

  @Override
  public List<String> findPermissions(List<Long> resourceIds) {
    try (Session session = sessionFactory.createSession()) {
      return session.dsl()
        .selectFrom(Resource.class)
        .where(field(Resource::getId).in(resourceIds))
        .execute().stream()
        .map(Resource::getPermission)
        .toList();
    }
  }
}
