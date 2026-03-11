package dev.flexmodel.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import dev.flexmodel.codegen.entity.ApiRequestLog;
import dev.flexmodel.domain.model.api.ApiRequestLogRepository;
import dev.flexmodel.domain.model.api.LogApiRank;
import dev.flexmodel.domain.model.api.LogStat;
import dev.flexmodel.query.Direction;
import dev.flexmodel.query.Predicate;
import dev.flexmodel.query.Query;
import dev.flexmodel.session.Session;

import java.util.List;

import static dev.flexmodel.query.Query.dateFormat;
import static dev.flexmodel.query.Query.field;

@ApplicationScoped
public class ApiLogFmRepository extends AbstractRepository implements ApiRequestLogRepository {

  @Override
  public List<ApiRequestLog> find(String projectId, Predicate filter, Integer page, Integer size) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl().selectFrom(ApiRequestLog.class)
        .where(dev.flexmodel.query.Expressions.field(ApiRequestLog::getProjectId).eq(projectId).and(filter))
        .orderBy("id", Direction.DESC)
        .page(page, size)
        .execute();
    }
  }


  @Override
  public List<LogStat> stat(String projectId, Predicate filter, String fmt) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl()
        .select(query -> query
          .field("date", dateFormat(field("created_at"), fmt))
          .field("total", Query.count(field("id"))))
        .from(ApiRequestLog.class)
        .where(dev.flexmodel.query.Expressions.field(ApiRequestLog::getProjectId).eq(projectId).and(filter))
        .groupBy("date")
        .execute(LogStat.class);
    }
  }

  @Override
  public List<LogApiRank> ranking(String projectId, Predicate filter) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl()
        .select(query -> query
          .field("name", field("path"))
          .field("total", Query.count(field("id"))))
        .from(ApiRequestLog.class)
        .where(dev.flexmodel.query.Expressions.field(ApiRequestLog::getProjectId).eq(projectId).and(filter))
        .groupBy("path")
        .orderBy("total", Direction.DESC)
        .page(1, 20)
        .execute(LogApiRank.class);
    }
  }

  @Override
  public ApiRequestLog save(ApiRequestLog record) {
    try (Session session = getProjectSession(record.getProjectId())) {
      session.dsl()
        .mergeInto(ApiRequestLog.class)
        .values(record)
        .execute();
    }
    return record;
  }

  @Override
  public void delete(Predicate unaryOperator) {
    throw new UnsupportedOperationException("Delete with predicate requires projectId");
  }

  @Override
  public long count(String projectId, Predicate filter) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl()
        .selectFrom(ApiRequestLog.class)
        .where(dev.flexmodel.query.Expressions.field(ApiRequestLog::getProjectId).eq(projectId).and(filter))
        .count();
    }
  }
}
