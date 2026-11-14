package dev.flexmodel.apps.flow;

import dev.flexmodel.codegen.entity.NodeInstanceLog;
import dev.flexmodel.common.AbstractRepository;
import dev.flexmodel.query.Predicate;
import dev.flexmodel.session.Session;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dev.flexmodel.codegen.System.nodeInstanceLog;
import static dev.flexmodel.query.Expressions.TRUE;
import static dev.flexmodel.query.Expressions.field;

@ApplicationScoped
public class AppFlowNodeInstanceLogRepository extends AbstractRepository {

  public boolean existsByEventKey(String appId, String eventKey) {
    return !findAll(appId, nodeInstanceLog.eventKey.eq(eventKey)).isEmpty();
  }

  public List<Map<String, Object>> findByFlowInstance(String appId, String flowInstanceId) {
    return findAll(appId, nodeInstanceLog.flowInstanceId.eq(flowInstanceId));
  }

  public void insert(String appId, Map<String, Object> log) {
    try (Session session = openSession()) {
      var row = new LinkedHashMap<>(log);
      row.put("project_id", appId);
      session.dsl().insertInto(NodeInstanceLog.class).values(row).execute();
    }
  }

  private List<Map<String, Object>> findAll(String appId, Predicate predicate) {
    try (Session session = openSession()) {
      return session.dsl().selectFrom(NodeInstanceLog.class)
        .where(field("project_id").eq(appId).and(predicate == null ? TRUE : predicate))
        .executeMaps();
    }
  }
}
