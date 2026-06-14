package dev.flexmodel.projectauth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import dev.flexmodel.codegen.entity.AuthProviderConfig;
import dev.flexmodel.session.Session;
import dev.flexmodel.session.SessionFactory;

import java.util.List;

import static dev.flexmodel.query.Expressions.field;

@ApplicationScoped
public class AuthProviderConfigFmRepository implements AuthProviderConfigRepository {

  @Inject
  SessionFactory sessionFactory;

  @Override
  public List<AuthProviderConfig> findByProjectId(String projectId) {
    try (Session session = sessionFactory.createSession()) {
      return session.dsl()
        .selectFrom(AuthProviderConfig.class)
        .execute();
    }
  }

  @Override
  public AuthProviderConfig find(String projectId, String name) {
    try (Session session = sessionFactory.createSession()) {
      return session.dsl()
        .selectFrom(AuthProviderConfig.class)
        .where(field(AuthProviderConfig::getName).eq(name))
        .executeOne();
    }
  }

  @Override
  public AuthProviderConfig save(AuthProviderConfig config) {
    try (Session session = sessionFactory.createSession()) {
      session.dsl()
        .mergeInto(AuthProviderConfig.class)
        .values(config)
        .execute();
    }
    return config;
  }

  @Override
  public void delete(String projectId, String name) {
    try (Session session = sessionFactory.createSession()) {
      session.dsl()
        .deleteFrom(AuthProviderConfig.class)
        .where(field(AuthProviderConfig::getName).eq(name))
        .execute();
    }
  }

  @Override
  public void deleteByProjectId(String projectId) {
    try (Session session = sessionFactory.createSession()) {
      session.dsl()
        .deleteFrom(AuthProviderConfig.class)
        .execute();
    }
  }
}
