package dev.flexmodel.projectauth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import dev.flexmodel.codegen.entity.AuthProviderConfig;
import dev.flexmodel.session.Session;

import java.util.List;

import static dev.flexmodel.query.Expressions.field;

@ApplicationScoped
public class AuthProviderConfigFmRepository implements AuthProviderConfigRepository {

  @Inject
  Session session;

  @Override
  public List<AuthProviderConfig> findByProjectId(String projectId) {
    return session.dsl()
      .selectFrom(AuthProviderConfig.class)
      .where(field(AuthProviderConfig::getProjectId).eq(projectId))
      .execute();
  }

  @Override
  public AuthProviderConfig find(String projectId, String name) {
    return session.dsl()
      .selectFrom(AuthProviderConfig.class)
      .where(field(AuthProviderConfig::getProjectId).eq(projectId)
        .and(field(AuthProviderConfig::getName).eq(name)))
      .executeOne();
  }

  @Override
  public AuthProviderConfig save(AuthProviderConfig config) {
    session.dsl()
      .mergeInto(AuthProviderConfig.class)
      .values(config)
      .execute();
    return config;
  }

  @Override
  public void delete(String projectId, String name) {
    session.dsl()
      .deleteFrom(AuthProviderConfig.class)
      .where(field(AuthProviderConfig::getProjectId).eq(projectId)
        .and(field(AuthProviderConfig::getName).eq(name)))
      .execute();
  }

  @Override
  public void deleteByProjectId(String projectId) {
    session.dsl()
      .deleteFrom(AuthProviderConfig.class)
      .where(field(AuthProviderConfig::getProjectId).eq(projectId))
      .execute();
  }
}
