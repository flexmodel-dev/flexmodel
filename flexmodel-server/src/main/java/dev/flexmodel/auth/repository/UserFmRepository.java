package dev.flexmodel.auth.repository;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import dev.flexmodel.codegen.entity.User;
import dev.flexmodel.session.Session;
import dev.flexmodel.session.SessionFactory;

import java.util.List;

import static dev.flexmodel.query.Expressions.field;

/**
 * @author cjbi
 */
@Singleton
public class UserFmRepository implements UserRepository {

  @Inject
  SessionFactory sessionFactory;

  @Override
  public User findByUsername(String username) {
    try (Session session = sessionFactory.createSession()) {
      return session.dsl()
        .selectFrom(User.class)
        .where(field(User::getId).eq(username)
          .or(field(User::getEmail).eq(username)))
        .executeOne();
    }
  }

  @Override
  public User findById(String userId) {
    try (Session session = sessionFactory.createSession()) {
      return session.dsl()
        .selectFrom(User.class)
        .where(field(User::getId).eq(userId))
        .executeOne();
    }
  }

  @Override
  public List<User> findAll() {
    try (Session session = sessionFactory.createSession()) {
      return session.dsl()
        .selectFrom(User.class)
        .execute();
    }
  }

  @Override
  public User save(User user) {
    try (Session session = sessionFactory.createSession()) {
      session.dsl()
        .mergeInto(User.class)
        .values(user)
        .execute();
    }
    return user;
  }

  @Override
  public void delete(String userId) {
    try (Session session = sessionFactory.createSession()) {
      session.dsl()
        .deleteFrom(User.class)
        .where(field(User::getId).eq(userId))
        .execute();
    }
  }
}
