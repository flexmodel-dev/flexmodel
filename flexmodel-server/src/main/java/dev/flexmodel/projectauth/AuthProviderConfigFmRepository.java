package dev.flexmodel.projectauth;

import dev.flexmodel.codegen.entity.AuthProviderConfig;
import dev.flexmodel.session.Session;
import dev.flexmodel.session.SessionFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

import static dev.flexmodel.codegen.System.authProviderConfig;

@ApplicationScoped
public class AuthProviderConfigFmRepository implements AuthProviderConfigRepository {

  @Inject
  SessionFactory sessionFactory;

  @Override
  public List<AuthProviderConfig> findByProjectId(String projectId) {
    try (Session session = sessionFactory.createSession(projectId)) {
      return session.dsl()
        .selectFrom(AuthProviderConfig.class)
        .execute();
    }
  }

  @Override
  public AuthProviderConfig find(String projectId, String name) {
    try (Session session = sessionFactory.createSession(projectId)) {
      return session.dsl()
        .selectFrom(AuthProviderConfig.class)
        .where(authProviderConfig.name.eq(name))
        .executeOne();
    }
  }

  @Override
  public AuthProviderConfig save(String projectId, AuthProviderConfig config) {
    try (Session session = sessionFactory.createSession(projectId)) {
      session.dsl()
        .mergeInto(AuthProviderConfig.class)
        .values(config)
        .execute();
    }
    return config;
  }

  @Override
  public void delete(String projectId, String name) {
    try (Session session = sessionFactory.createSession(projectId)) {
      session.dsl()
        .deleteFrom(AuthProviderConfig.class)
        .where(authProviderConfig.name.eq(name))
        .execute();
    }
  }

}
