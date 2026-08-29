package dev.flexmodel.observability;

import dev.flexmodel.codegen.entity.Span;
import dev.flexmodel.query.Direction;
import dev.flexmodel.query.Expressions;
import dev.flexmodel.query.Predicate;
import dev.flexmodel.session.Session;
import dev.flexmodel.session.SessionFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

import static dev.flexmodel.codegen.System.span;

/**
 * @author cjbi
 */
@ApplicationScoped
public class SpanFmRepository implements SpanRepository {

  @Inject
  SessionFactory sessionFactory;

  @Override
  public List<Span> findByProject(String projectId, Long startFrom, Long startTo, int limit) {
    Predicate filter = Expressions.TRUE;
    if (projectId != null) {
      filter = filter.and(span.projectId.eq(projectId));
    }
    if (startFrom != null) {
      filter = filter.and(span.startTime.gte(startFrom));
    }
    if (startTo != null) {
      filter = filter.and(span.startTime.lte(startTo));
    }
    try (Session session = sessionFactory.createSession()) {
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
    if (projectId != null) {
      filter = filter.and(span.projectId.eq(projectId));
    }
    if (startFrom != null) {
      filter = filter.and(span.startTime.gte(startFrom));
    }
    if (startTo != null) {
      filter = filter.and(span.startTime.lte(startTo));
    }
    if (traceId != null && !traceId.isBlank()) {
      filter = filter.and(span.traceId.contains(traceId));
    }
    try (Session session = sessionFactory.createSession()) {
      return session.dsl().selectFrom(Span.class)
        .where(filter)
        .orderBy(span.startTime, Direction.DESC)
        .page(1, limit)
        .execute();
    }
  }

  @Override
  public List<Span> findByTraceId(String traceId) {
    try (Session session = sessionFactory.createSession()) {
      return session.dsl().selectFrom(Span.class)
        .where(span.traceId.eq(traceId))
        .orderBy(span.startTime, Direction.ASC)
        .execute();
    }
  }
}
