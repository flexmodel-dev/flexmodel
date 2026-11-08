package dev.flexmodel.apps.flow;

import dev.flexmodel.codegen.entity.AppFlowInstance;
import dev.flexmodel.common.AbstractRepository;
import dev.flexmodel.common.NotFoundException;
import dev.flexmodel.query.Predicate;
import dev.flexmodel.reflect.ReflectionUtils;
import dev.flexmodel.session.Session;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dev.flexmodel.codegen.System.appFlowInstance;
import static dev.flexmodel.query.Expressions.TRUE;
import static dev.flexmodel.query.Expressions.field;

@ApplicationScoped
public class AppFlowInstanceRepository extends AbstractRepository {

  public Map<String, Object> get(String appId, String id) {
    Map<String, Object> row = findOne(appId, id);
    if (row == null) throw new NotFoundException("审批实例不存在");
    return row;
  }

  public Map<String, Object> find(String appId, String id) {
    return findOne(appId, id);
  }

  public List<Map<String, Object>> findAll(String appId) {
    return findAll(appId, null);
  }

  public List<Map<String, Object>> findByRequestKey(String appId, String requestKey) {
    return findAll(appId, appFlowInstance.requestKey.eq(requestKey));
  }

  public List<Map<String, Object>> findByFlowInstanceId(String appId, String flowInstanceId) {
    return findAll(appId, appFlowInstance.flowInstanceId.eq(flowInstanceId));
  }

  public List<Map<String, Object>> findByInitiator(String appId, String initiator) {
    return findAll(appId, appFlowInstance.initiator.eq(initiator));
  }

  public List<Map<String, Object>> findByFormRecord(String appId, String formName, String recordId) {
    return findAll(appId, appFlowInstance.formName.eq(formName).and(appFlowInstance.recordId.eq(recordId)));
  }

  public List<Map<String, Object>> findByParentTaskIds(String appId, Collection<String> parentTaskIds) {
    return parentTaskIds.isEmpty() ? List.of()
      : findAll(appId, appFlowInstance.parentTaskId.in(parentTaskIds));
  }

  public void insert(String appId, Map<String, Object> instance) {
    try (Session session = openSession()) {
      var row = new LinkedHashMap<>(instance);
      row.put("app_id", appId);
      session.data().insert(ReflectionUtils.getModelNameFromClass(AppFlowInstance.class), row);
    }
  }

  public void update(String appId, String id, Map<String, Object> values) {
    try (Session session = openSession()) {
      var update = session.dsl().update(AppFlowInstance.class);
      values.forEach(update::set);
      update.where(field("app_id").eq(appId).and(field("id").eq(id))).execute();
    }
  }

  private List<Map<String, Object>> findAll(String appId, Predicate predicate) {
    try (Session session = openSession()) {
      return session.data().findAll(ReflectionUtils.getModelNameFromClass(AppFlowInstance.class),
        field("app_id").eq(appId).and(predicate == null ? TRUE : predicate));
    }
  }

  private Map<String, Object> findOne(String appId, String id) {
    try (Session session = openSession()) {
      return session.data().findById(ReflectionUtils.getModelNameFromClass(AppFlowInstance.class), id)
        .filter(row -> appId.equals(row.get("app_id")))
        .orElse(null);
    }
  }
}
