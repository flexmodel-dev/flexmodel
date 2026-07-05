package dev.flexmodel.api;

import dev.flexmodel.codegen.entity.ApiRequestLog;
import dev.flexmodel.common.AbstractRepository;
import dev.flexmodel.query.Direction;
import dev.flexmodel.query.Predicate;
import dev.flexmodel.query.Query;
import dev.flexmodel.session.Session;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

import static dev.flexmodel.codegen.System.apiRequestLog;
import static dev.flexmodel.query.Query.dateFormat;
import static dev.flexmodel.query.Query.field;

@ApplicationScoped
public class ApiLogFmRepository extends AbstractRepository implements ApiRequestLogRepository {

  @Override
  public List<ApiRequestLog> find(String projectId, Predicate filter, Integer page, Integer size) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl().selectFrom(ApiRequestLog.class)
        .where(filter)
        .orderBy(apiRequestLog.id, Direction.DESC)
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
        .where(filter)
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
        .where(filter)
        .groupBy("path")
        .orderBy("total", Direction.DESC)
        .page(1, 20)
        .execute(LogApiRank.class);
    }
  }

  @Override
  public ApiRequestLog save(String projectId, ApiRequestLog record) {
    try (Session session = getProjectSession(projectId)) {
      session.dsl()
        .mergeInto(ApiRequestLog.class)
        .values(record)
        .execute();
    }
    return record;
  }

  @Override
  public void delete(String projectId, Predicate filter) {
    try (Session session = getProjectSession(projectId)) {
      session.dsl()
        .deleteFrom(ApiRequestLog.class)
        .where(filter)
        .execute();
    }
  }

  @Override
  public long count(String projectId, Predicate filter) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl()
        .selectFrom(ApiRequestLog.class)
        .where(filter)
        .count();
    }
  }
}
