package dev.flexmodel.auth.repository;

import dev.flexmodel.codegen.entity.LoginLog;
import dev.flexmodel.common.AbstractRepository;
import dev.flexmodel.query.Direction;
import dev.flexmodel.session.Session;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

import static dev.flexmodel.codegen.System.loginLog;

/**
 * @author cjbi
 */
@ApplicationScoped
public class LoginLogFmRepository extends AbstractRepository implements LoginLogRepository {

  @Override
  public void save(LoginLog log) {
    try (Session session = openSession()) {
      session.dsl()
        .mergeInto(LoginLog.class)
        .values(log)
        .execute();
    }
  }

  @Override
  public List<LoginLog> findByUserId(String userId, int page, int size) {
    try (Session session = openSession()) {
      return session.dsl()
        .selectFrom(LoginLog.class)
        .where(loginLog.userId.eq(userId))
        .orderBy(loginLog.createdAt, Direction.DESC)
        .page(page, size)
        .execute();
    }
  }

  @Override
  public long countByUserId(String userId) {
    try (Session session = openSession()) {
      return session.dsl()
        .selectFrom(LoginLog.class)
        .where(loginLog.userId.eq(userId))
        .count();
    }
  }
}
