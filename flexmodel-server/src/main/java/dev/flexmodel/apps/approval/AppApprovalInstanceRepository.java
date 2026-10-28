package dev.flexmodel.apps.approval;

import dev.flexmodel.codegen.entity.AppApprovalInstance;
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

import static dev.flexmodel.codegen.System.appApprovalInstance;
import static dev.flexmodel.query.Expressions.TRUE;
import static dev.flexmodel.query.Expressions.field;

@ApplicationScoped
public class AppApprovalInstanceRepository extends AbstractRepository {

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
    return findAll(appId, appApprovalInstance.requestKey.eq(requestKey));
  }

  public List<Map<String, Object>> findByFlowInstanceId(String appId, String flowInstanceId) {
    return findAll(appId, appApprovalInstance.flowInstanceId.eq(flowInstanceId));
  }

  public List<Map<String, Object>> findByInitiator(String appId, String initiator) {
    return findAll(appId, appApprovalInstance.initiator.eq(initiator));
  }

  public List<Map<String, Object>> findByFormRecord(String appId, String formName, String recordId) {
    return findAll(appId, appApprovalInstance.formName.eq(formName).and(appApprovalInstance.recordId.eq(recordId)));
  }

  public List<Map<String, Object>> findByParentTaskIds(String appId, Collection<String> parentTaskIds) {
    return parentTaskIds.isEmpty() ? List.of()
      : findAll(appId, appApprovalInstance.parentTaskId.in(parentTaskIds));
  }

  public void insert(String appId, Map<String, Object> instance) {
    try (Session session = openSession()) {
      var row = new LinkedHashMap<>(instance);
      row.put("project_id", appId);
      session.data().insert(ReflectionUtils.getModelNameFromClass(AppApprovalInstance.class), row);
    }
  }

  public void update(String appId, String id, Map<String, Object> values) {
    try (Session session = openSession()) {
      var update = session.dsl().update(AppApprovalInstance.class);
      values.forEach(update::set);
      update.where(field("project_id").eq(appId).and(field("id").eq(id))).execute();
    }
  }

  private List<Map<String, Object>> findAll(String appId, Predicate predicate) {
    try (Session session = openSession()) {
      return session.data().findAll(ReflectionUtils.getModelNameFromClass(AppApprovalInstance.class),
        field("project_id").eq(appId).and(predicate == null ? TRUE : predicate));
    }
  }

  private Map<String, Object> findOne(String appId, String id) {
    try (Session session = openSession()) {
      return session.data().findById(ReflectionUtils.getModelNameFromClass(AppApprovalInstance.class), id)
        .filter(row -> appId.equals(row.get("project_id")))
        .orElse(null);
    }
  }
}
