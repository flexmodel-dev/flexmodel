package dev.flexmodel.approval;

import dev.flexmodel.common.AbstractRepository;
import dev.flexmodel.common.NotFoundException;
import dev.flexmodel.query.Predicate;
import dev.flexmodel.session.Session;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.*;

import static dev.flexmodel.query.Expressions.*;
import static dev.flexmodel.approval.ApprovalJson.*;
import static dev.flexmodel.common.Values.*;

@ApplicationScoped
public class ApprovalRepository extends AbstractRepository {
  public Session open(String appId) {
    return getProjectSession(appId);
  }

  public Map<String, Object> definition(String appId, String moduleId) {
    var rows = list(appId, "f_em_flow_definition", field("flow_module_id").eq(moduleId).and(field("is_deleted").eq(false)));
    if (rows.isEmpty()) throw new NotFoundException("流程不存在或已删除");
    return rows.getFirst();
  }

  public void updateDefinition(String appId, String moduleId, Map<String, Object> values) {
    try (Session session = open(appId)) {
      // Preserve the current row when normalizing model defaults during a partial update.
      // In particular, restoring created_* must not reset an explicitly disabled status to 0.
      var merged = new LinkedHashMap<>(definition(appId, moduleId));
      merged.putAll(values);
      session.dsl().update("f_em_flow_definition").values(merged).where(field("flow_module_id").eq(moduleId)).execute();
    }
  }

  public Map<String, Object> deployment(String appId, String deploymentId) {
    var rows = list(appId, "f_em_flow_deployment", field("flow_deploy_id").eq(deploymentId));
    if (rows.isEmpty()) throw new NotFoundException("流程部署版本不存在");
    return rows.getFirst();
  }

  public Map<String, Object> deployedFlow(String appId, String deploymentId) {
    return ApprovalFlowModel.decode(deployment(appId, deploymentId));
  }

  public Map<String, Object> get(String appId, String table, String id) {
    try (Session session = open(appId)) {
      return session.data().findById(table, id).orElseThrow(() -> new NotFoundException("对象不存在或已删除"));
    }
  }

  public Map<String, Object> optional(String appId, String table, String id) {
    try (Session session = open(appId)) {
      return session.data().findById(table, id).orElse(null);
    }
  }

  public List<Map<String, Object>> list(String appId, String table, Predicate predicate) {
    try (Session session = open(appId)) {
      return session.dsl().selectFrom(table).where(predicate == null ? TRUE : predicate).execute();
    }
  }

  public void insert(String appId, String table, Map<String, Object> value) {
    try (Session session = open(appId)) {
      session.data().insert(table, new LinkedHashMap<>(value));
    }
  }

  public void update(String appId, String table, String id, Map<String, Object> value) {
    try (Session session = open(appId)) {
      session.data().updateById(table, new LinkedHashMap<>(value), id);
    }
  }

  public void delete(String appId, String table, String id) {
    try (Session session = open(appId)) {
      session.data().deleteById(table, id);
    }
  }
}
