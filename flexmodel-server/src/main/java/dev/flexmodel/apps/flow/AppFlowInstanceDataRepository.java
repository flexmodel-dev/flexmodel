package dev.flexmodel.apps.flow;

import dev.flexmodel.codegen.entity.InstanceData;
import dev.flexmodel.common.AbstractRepository;
import dev.flexmodel.query.Predicate;
import dev.flexmodel.session.Session;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dev.flexmodel.codegen.System.instanceData;
import static dev.flexmodel.query.Expressions.TRUE;
import static dev.flexmodel.query.Expressions.field;

@ApplicationScoped
public class AppFlowInstanceDataRepository extends AbstractRepository {

  public List<Map<String, Object>> findByFlowInstance(String appId, String flowInstanceId) {
    return findAll(appId, instanceData.flowInstanceId.eq(flowInstanceId));
  }

  public Map<String, Object> findLatest(String appId, String flowInstanceId) {
    return findByFlowInstance(appId, flowInstanceId).stream()
      .max(Comparator.comparingLong(row -> ((Number) row.get("id")).longValue()))
      .orElse(null);
  }

  public List<Map<String, Object>> findByDataKey(String appId, String dataKey) {
    return findAll(appId, instanceData.instanceDataId.eq(dataKey));
  }

  public void insert(String appId, Map<String, Object> data) {
    try (Session session = openSession()) {
      var row = new LinkedHashMap<>(data);
      row.put("project_id", appId);
      session.dsl().insertInto(InstanceData.class).values(row).execute();
    }
  }

  private List<Map<String, Object>> findAll(String appId, Predicate predicate) {
    try (Session session = openSession()) {
      return session.dsl().selectFrom(InstanceData.class)
        .where(field("project_id").eq(appId).and(predicate == null ? TRUE : predicate))
        .executeMaps();
    }
  }
}
