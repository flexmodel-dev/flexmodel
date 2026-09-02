package dev.flexmodel.data;

import dev.flexmodel.codegen.entity.AuditLog;
import dev.flexmodel.common.AbstractRepository;
import dev.flexmodel.query.Direction;
import dev.flexmodel.query.Expressions;
import dev.flexmodel.query.Predicate;
import dev.flexmodel.session.Session;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;

import java.util.List;

import static dev.flexmodel.codegen.System.auditLog;

/**
 * 审计日志仓储实现。
 *
 * @author cjbi
 */
@ApplicationScoped
@ActivateRequestContext
public class AuditLogFmRepository extends AbstractRepository implements AuditLogRepository {

  @Override
  public AuditLog save(String projectId, AuditLog log) {
    try (Session session = getProjectSession(projectId)) {
      session.dsl().mergeInto(AuditLog.class).values(log).execute();
    }
    return log;
  }

  @Override
  public List<AuditLog> find(String projectId, Predicate filter, Integer page, Integer size) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl()
        .selectFrom(AuditLog.class)
        .where(filter)
        .page(page, size)
        .orderBy(auditLog.createdAt, Direction.DESC)
        .execute();
    }
  }

  @Override
  public long count(String projectId, Predicate filter) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl()
        .selectFrom(AuditLog.class)
        .where(filter)
        .count();
    }
  }

  @Override
  public List<AuditLog> findByTraceId(String projectId, String traceId, int limit) {
    if (traceId == null || traceId.isEmpty()) {
      return List.of();
    }
    try (Session session = getProjectSession(projectId)) {
      return session.dsl()
        .selectFrom(AuditLog.class)
        .where(Expressions.TRUE.and(auditLog.traceId.eq(traceId)))
        .orderBy(auditLog.createdAt, Direction.DESC)
        .page(1, limit)
        .execute();
    }
  }

  @Override
  public void delete(String projectId, Predicate filter) {
    try (Session session = getProjectSession(projectId)) {
      session.dsl()
        .deleteFrom(AuditLog.class)
        .where(filter)
        .execute();
    }
  }
}
