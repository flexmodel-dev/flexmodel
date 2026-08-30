package dev.flexmodel.observability;

import dev.flexmodel.codegen.entity.Span;
import dev.flexmodel.common.AbstractRepository;
import dev.flexmodel.query.Direction;
import dev.flexmodel.query.Expressions;
import dev.flexmodel.query.Predicate;
import dev.flexmodel.session.Session;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDateTime;
import java.util.List;

import static dev.flexmodel.codegen.System.span;

/**
 * 链路追踪 Span 仓储（项目级，每个项目库各自持有 f_span 表）。
 *
 * @author cjbi
 */
@ApplicationScoped
public class SpanFmRepository extends AbstractRepository implements SpanRepository {

  @Override
  public List<Span> findByProject(String projectId, Long startFrom, Long startTo, int limit) {
    Predicate filter = Expressions.TRUE;
    if (startFrom != null) {
      filter = filter.and(span.startTime.gte(startFrom));
    }
    if (startTo != null) {
      filter = filter.and(span.startTime.lte(startTo));
    }
    try (Session session = getProjectSession(projectId)) {
      return session.dsl().selectFrom(Span.class)
        .where(filter)
        .orderBy(span.startTime, Direction.DESC)
        .page(1, limit)
        .execute();
    }
  }

  @Override
  public List<Span> findByProject(String projectId, Long startFrom, Long startTo, String traceId, int limit) {
    Predicate filter = Expressions.TRUE;
    if (startFrom != null) {
      filter = filter.and(span.startTime.gte(startFrom));
    }
    if (startTo != null) {
      filter = filter.and(span.startTime.lte(startTo));
    }
    if (traceId != null && !traceId.isBlank()) {
      filter = filter.and(span.traceId.contains(traceId));
    }
    try (Session session = getProjectSession(projectId)) {
      return session.dsl().selectFrom(Span.class)
        .where(filter)
        .orderBy(span.startTime, Direction.DESC)
        .page(1, limit)
        .execute();
    }
  }

  @Override
  public List<Span> findByTraceId(String projectId, String traceId) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl().selectFrom(Span.class)
        .where(Expressions.TRUE.and(span.traceId.eq(traceId)))
        .orderBy(span.startTime, Direction.ASC)
        .execute();
    }
  }

  @Override
  public void purgeOldLogs(String projectId, int maxDays) {
    LocalDateTime purgeDate = LocalDateTime.now().minusDays(maxDays);
    Predicate filter = span.createdAt.lte(purgeDate);
    try (Session session = getProjectSession(projectId)) {
      session.dsl()
        .deleteFrom(Span.class)
        .where(filter)
        .execute();
    }
  }
}
