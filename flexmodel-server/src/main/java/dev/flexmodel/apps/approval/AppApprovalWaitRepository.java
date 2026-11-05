package dev.flexmodel.apps.approval;

import dev.flexmodel.codegen.entity.AppApprovalWait;
import dev.flexmodel.common.AbstractRepository;
import dev.flexmodel.common.NotFoundException;
import dev.flexmodel.query.Predicate;
import dev.flexmodel.reflect.ReflectionUtils;
import dev.flexmodel.session.Session;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dev.flexmodel.codegen.System.appApprovalWait;
import static dev.flexmodel.query.Expressions.TRUE;
import static dev.flexmodel.query.Expressions.field;

@ApplicationScoped
public class AppApprovalWaitRepository extends AbstractRepository {

  public Map<String, Object> get(String appId, String id) {
    Map<String, Object> row = findOne(appId, id);
    if (row == null) throw new NotFoundException("审批挂起任务不存在");
    return row;
  }

  public Map<String, Object> find(String appId, String id) {
    return findOne(appId, id);
  }

  public List<Map<String, Object>> findByRunId(String appId, String runId) {
    return findAll(appId, appApprovalWait.runId.eq(runId));
  }

  public void insert(String appId, Map<String, Object> wait) {
    try (Session session = openSession()) {
      var row = new LinkedHashMap<>(wait);
      row.put("app_id", appId);
      session.data().insert(ReflectionUtils.getModelNameFromClass(AppApprovalWait.class), row);
    }
  }

  public void update(String appId, String id, Map<String, Object> values) {
    try (Session session = openSession()) {
      var update = session.dsl().update(AppApprovalWait.class);
      values.forEach(update::set);
      update.where(field("app_id").eq(appId).and(field("id").eq(id))).execute();
    }
  }

  private List<Map<String, Object>> findAll(String appId, Predicate predicate) {
    try (Session session = openSession()) {
      return session.data().findAll(ReflectionUtils.getModelNameFromClass(AppApprovalWait.class),
        field("app_id").eq(appId).and(predicate == null ? TRUE : predicate));
    }
  }

  private Map<String, Object> findOne(String appId, String id) {
    try (Session session = openSession()) {
      return session.data().findById(ReflectionUtils.getModelNameFromClass(AppApprovalWait.class), id)
        .filter(row -> appId.equals(row.get("app_id")))
        .orElse(null);
    }
  }
}
