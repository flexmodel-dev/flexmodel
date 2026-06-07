package dev.flexmodel.auth.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import dev.flexmodel.codegen.entity.AuthApiKey;
import dev.flexmodel.session.Session;
import dev.flexmodel.session.SessionFactory;

import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class ApiKeyFmRepository implements ApiKeyRepository {

  @Inject
  SessionFactory sessionFactory;

  @Override
  public List<AuthApiKey> findAll() {
    try (Session session = sessionFactory.createSession()) {
      return session.dsl()
        .selectFrom(AuthApiKey.class)
        .execute();
    }
  }

  @Override
  public AuthApiKey findByKeyHash(String keyHash) {
    try (Session session = sessionFactory.createSession()) {
      return session.dsl()
        .selectFrom(AuthApiKey.class)
        .where(dev.flexmodel.query.Expressions.field(AuthApiKey::getKeyHash).eq(keyHash))
        .executeOne();
    }
  }

  @Override
  public AuthApiKey find(String id) {
    try (Session session = sessionFactory.createSession()) {
      return session.dsl()
        .selectFrom(AuthApiKey.class)
        .where(dev.flexmodel.query.Expressions.field(AuthApiKey::getId).eq(id))
        .executeOne();
    }
  }

  @Override
  public AuthApiKey save(AuthApiKey apiKey) {
    try (Session session = sessionFactory.createSession()) {
      session.dsl()
        .mergeInto(AuthApiKey.class)
        .values(apiKey)
        .execute();
    }
    return apiKey;
  }

  @Override
  public void delete(String id) {
    try (Session session = sessionFactory.createSession()) {
      session.dsl()
        .deleteFrom(AuthApiKey.class)
        .where(dev.flexmodel.query.Expressions.field(AuthApiKey::getId).eq(id))
        .execute();
    }
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
