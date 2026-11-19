package dev.flexmodel.projectauth;

import dev.flexmodel.codegen.entity.AuthProviderConfig;
import dev.flexmodel.session.Session;
import dev.flexmodel.session.SessionFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.UUID;

import static dev.flexmodel.codegen.System.authProviderConfig;

@ApplicationScoped
public class AuthProviderConfigFmRepository implements AuthProviderConfigRepository {

  @Inject
  SessionFactory sessionFactory;

  private Session open() {
    return sessionFactory.createSession();
  }

  @Override
  public List<AuthProviderConfig> findByProjectId(String projectId) {
    try (Session session = open()) {
      return session.dsl()
        .selectFrom(AuthProviderConfig.class)
        .where(authProviderConfig.projectId.eq(projectId))
        .execute();
    }
  }

  @Override
  public AuthProviderConfig find(String projectId, String name) {
    try (Session session = open()) {
      return session.dsl()
        .selectFrom(AuthProviderConfig.class)
        .where(authProviderConfig.projectId.eq(projectId).and(authProviderConfig.name.eq(name)))
        .executeOne();
    }
  }

  @Override
  public AuthProviderConfig save(String projectId, AuthProviderConfig config) {
    config.setProjectId(projectId);
    if (config.getId() == null || config.getId().isBlank()) {
      AuthProviderConfig existing = find(projectId, config.getName());
      config.setId(existing != null ? existing.getId() : UUID.randomUUID().toString());
    }
    try (Session session = open()) {
      session.dsl()
        .mergeInto(AuthProviderConfig.class)
        .values(config)
        .execute();
    }
    return config;
  }

  @Override
  public void delete(String projectId, String name) {
    try (Session session = open()) {
      session.dsl()
        .deleteFrom(AuthProviderConfig.class)
        .where(authProviderConfig.projectId.eq(projectId).and(authProviderConfig.name.eq(name)))
        .execute();
    }
  }

}
