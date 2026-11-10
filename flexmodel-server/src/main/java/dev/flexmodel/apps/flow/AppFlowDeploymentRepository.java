package dev.flexmodel.apps.flow;

import dev.flexmodel.codegen.entity.FlowDeployment;
import dev.flexmodel.common.AbstractRepository;
import dev.flexmodel.common.NotFoundException;
import dev.flexmodel.query.Predicate;
import dev.flexmodel.session.Session;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Map;

import static dev.flexmodel.codegen.System.flowDeployment;
import static dev.flexmodel.query.Expressions.TRUE;
import static dev.flexmodel.query.Expressions.field;

@ApplicationScoped
public class AppFlowDeploymentRepository extends AbstractRepository {

  public Map<String, Object> get(String appId, String deploymentId) {
    var rows = findAll(appId, flowDeployment.flowDeployId.eq(deploymentId));
    if (rows.isEmpty()) throw new NotFoundException("流程部署版本不存在");
    return rows.getFirst();
  }

  public List<Map<String, Object>> findByModuleId(String appId, String moduleId) {
    return findAll(appId, flowDeployment.flowModuleId.eq(moduleId));
  }

  public Map<String, Object> deployedFlow(String appId, String deploymentId) {
    return AppFlowModel.decode(get(appId, deploymentId));
  }

  private List<Map<String, Object>> findAll(String appId, Predicate predicate) {
    try (Session session = openSession()) {
      return session.dsl().selectFrom(FlowDeployment.class)
        .where(field("project_id").eq(appId).and(predicate == null ? TRUE : predicate))
        .executeMaps();
    }
  }
}
