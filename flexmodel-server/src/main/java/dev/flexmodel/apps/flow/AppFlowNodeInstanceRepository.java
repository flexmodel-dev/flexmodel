package dev.flexmodel.apps.flow;

import dev.flexmodel.codegen.entity.NodeInstance;
import dev.flexmodel.common.AbstractRepository;
import dev.flexmodel.common.NotFoundException;
import dev.flexmodel.query.Predicate;
import dev.flexmodel.session.Session;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Map;

import static dev.flexmodel.codegen.System.nodeInstance;
import static dev.flexmodel.query.Expressions.TRUE;
import static dev.flexmodel.query.Expressions.field;

@ApplicationScoped
public class AppFlowNodeInstanceRepository extends AbstractRepository {

  public List<Map<String, Object>> findByFlowInstance(String appId, String flowInstanceId) {
    return findAll(appId, nodeInstance.flowInstanceId.eq(flowInstanceId));
  }

  public Map<String, Object> getByNodeInstanceId(String appId, String nodeInstanceId) {
    return findAll(appId, nodeInstance.nodeInstanceId.eq(nodeInstanceId)).stream()
      .findFirst()
      .orElseThrow(() -> new NotFoundException("节点实例不存在"));
  }

  private List<Map<String, Object>> findAll(String appId, Predicate predicate) {
    try (Session session = openSession()) {
      return session.dsl().selectFrom(NodeInstance.class)
        .where(field("project_id").eq(appId).and(predicate == null ? TRUE : predicate))
        .executeMaps();
    }
  }
}
