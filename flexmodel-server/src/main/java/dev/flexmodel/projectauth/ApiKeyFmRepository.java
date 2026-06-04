package dev.flexmodel.projectauth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import dev.flexmodel.codegen.entity.AuthApiKey;
import dev.flexmodel.session.Session;

import java.time.LocalDateTime;
import java.util.List;

import static dev.flexmodel.query.Expressions.field;

@ApplicationScoped
public class ApiKeyFmRepository implements ApiKeyRepository {

  @Inject
  Session session;

  @Override
  public List<AuthApiKey> findByProjectId(String projectId) {
    return session.dsl()
      .selectFrom(AuthApiKey.class)
      .where(field(AuthApiKey::getProjectId).eq(projectId))
      .execute();
  }

  @Override
  public AuthApiKey findByKeyHash(String keyHash) {
    return session.dsl()
      .selectFrom(AuthApiKey.class)
      .where(field(AuthApiKey::getKeyHash).eq(keyHash))
      .executeOne();
  }

  @Override
  public AuthApiKey find(String id) {
    return session.dsl()
      .selectFrom(AuthApiKey.class)
      .where(field(AuthApiKey::getId).eq(id))
      .executeOne();
  }

  @Override
  public AuthApiKey save(AuthApiKey apiKey) {
    session.dsl()
      .mergeInto(AuthApiKey.class)
      .values(apiKey)
      .execute();
    return apiKey;
  }

  @Override
  public void delete(String id) {
    session.dsl()
      .deleteFrom(AuthApiKey.class)
      .where(field(AuthApiKey::getId).eq(id))
      .execute();
  }

  @Override
  public void deleteByProjectId(String projectId) {
    session.dsl()
      .deleteFrom(AuthApiKey.class)
      .where(field(AuthApiKey::getProjectId).eq(projectId))
      .execute();
  }

  @Override
  public void updateLastUsedAt(String id, LocalDateTime lastUsedAt) {
    AuthApiKey existing = find(id);
    if (existing != null) {
      existing.setLastUsedAt(lastUsedAt);
      save(existing);
    }
  }
}
