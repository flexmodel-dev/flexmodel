package dev.flexmodel.apps.flow;

import dev.flexmodel.codegen.entity.FlowDefinition;
import dev.flexmodel.common.AbstractRepository;
import dev.flexmodel.common.NotFoundException;
import dev.flexmodel.common.ProjectUnitOfWork;
import dev.flexmodel.query.Predicate;
import dev.flexmodel.reflect.ReflectionUtils;
import dev.flexmodel.session.Session;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dev.flexmodel.codegen.System.flowDefinition;
import static dev.flexmodel.query.Expressions.TRUE;
import static dev.flexmodel.query.Expressions.field;

@ApplicationScoped
public class AppFlowDefinitionRepository extends AbstractRepository {

  public Map<String, Object> get(String appId, String moduleId) {
    Map<String, Object> row = find(appId, moduleId);
    if (row == null) throw new NotFoundException("流程不存在或已删除");
    return row;
  }

  public Map<String, Object> find(String appId, String moduleId) {
    var rows = findAll(appId, flowDefinition.flowModuleId.eq(moduleId).and(flowDefinition.isDeleted.eq(false)));
    return rows.isEmpty() ? null : rows.getFirst();
  }

  public List<Map<String, Object>> findAllActive(String appId) {
    return findAll(appId, flowDefinition.isDeleted.eq(false));
  }

  public List<Map<String, Object>> findByFlowKey(String appId, String flowKey) {
    return findAll(appId, flowDefinition.flowKey.eq(flowKey).and(flowDefinition.isDeleted.eq(false)));
  }

  public void update(String appId, String moduleId, Map<String, Object> values) {
    var merged = new LinkedHashMap<>(get(appId, moduleId));
    merged.putAll(values);
    try (Session session = openSession()) {
      var update = session.dsl().update(FlowDefinition.class);
      merged.forEach(update::set);
      update.where(flowDefinition.projectId.eq(appId).and(flowDefinition.flowModuleId.eq(moduleId))).execute();
    }
  }

  public void lock(String appId, String moduleId) {
    ProjectUnitOfWork.lock(appId, ReflectionUtils.getModelNameFromClass(FlowDefinition.class), "flow_module_id", moduleId);
  }

  private List<Map<String, Object>> findAll(String appId, Predicate predicate) {
    try (Session session = openSession()) {
      return session.dsl().selectFrom(FlowDefinition.class)
        .where(field("project_id").eq(appId).and(predicate == null ? TRUE : predicate))
        .executeMaps();
    }
  }
}
