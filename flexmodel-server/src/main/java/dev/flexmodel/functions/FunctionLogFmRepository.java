package dev.flexmodel.functions;

import dev.flexmodel.codegen.entity.FunctionLog;
import dev.flexmodel.common.AbstractRepository;
import dev.flexmodel.query.Direction;
import dev.flexmodel.query.Predicate;
import dev.flexmodel.session.Session;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

import static dev.flexmodel.codegen.System.functionLog;

/**
 * @author cjbi
 */
@ApplicationScoped
public class FunctionLogFmRepository extends AbstractRepository implements FunctionLogRepository {

  @Override
  public List<FunctionLog> find(String projectId, Predicate filter, Integer page, Integer size) {
    try (Session session = openSession()) {
      return session.dsl().selectFrom(FunctionLog.class)
        .where(functionLog.projectId.eq(projectId).and(filter))
        .orderBy(functionLog.createdAt, Direction.DESC)
        .page(page, size)
        .execute();
    }
  }

  @Override
  public long count(String projectId, Predicate filter) {
    try (Session session = openSession()) {
      return session.dsl()
        .selectFrom(FunctionLog.class)
        .where(functionLog.projectId.eq(projectId).and(filter))
        .count();
    }
  }

  @Override
  public void delete(String projectId, Predicate filter) {
    try (Session session = openSession()) {
      session.dsl()
        .deleteFrom(FunctionLog.class)
        .where(functionLog.projectId.eq(projectId).and(filter))
        .execute();
    }
  }
}
