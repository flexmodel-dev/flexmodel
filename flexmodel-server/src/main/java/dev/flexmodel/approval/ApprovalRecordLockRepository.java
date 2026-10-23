package dev.flexmodel.approval;

import dev.flexmodel.codegen.entity.ApprovalRecordLock;
import dev.flexmodel.common.AbstractRepository;
import dev.flexmodel.query.Predicate;
import dev.flexmodel.reflect.ReflectionUtils;
import dev.flexmodel.session.Session;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dev.flexmodel.codegen.System.approvalRecordLock;
import static dev.flexmodel.query.Expressions.TRUE;
import static dev.flexmodel.query.Expressions.field;

@ApplicationScoped
public class ApprovalRecordLockRepository extends AbstractRepository {

  public List<Map<String, Object>> findByRecord(String appId, String schemaName, String modelName, String recordId) {
    return findAll(appId, approvalRecordLock.schemaName.eq(schemaName)
      .and(approvalRecordLock.modelName.eq(modelName))
      .and(approvalRecordLock.recordId.eq(recordId)));
  }

  public List<Map<String, Object>> findBySchemaAndModel(String appId, String schemaName, String modelName) {
    return findAll(appId, approvalRecordLock.schemaName.eq(schemaName)
      .and(approvalRecordLock.modelName.eq(modelName)));
  }

  public List<Map<String, Object>> findByRunId(String appId, String runId) {
    return findAll(appId, approvalRecordLock.runId.eq(runId));
  }

  public void insert(String appId, Map<String, Object> lock) {
    try (Session session = openSession()) {
      var row = new LinkedHashMap<>(lock);
      row.put("project_id", appId);
      session.data().insert(ReflectionUtils.getModelNameFromClass(ApprovalRecordLock.class), row);
    }
  }

  public void delete(String appId, String id) {
    try (Session session = openSession()) {
      session.dsl().deleteFrom(ApprovalRecordLock.class)
        .where(field("project_id").eq(appId).and(field("id").eq(id))).execute();
    }
  }

  private List<Map<String, Object>> findAll(String appId, Predicate predicate) {
    try (Session session = openSession()) {
      return session.data().findAll(ReflectionUtils.getModelNameFromClass(ApprovalRecordLock.class),
        field("project_id").eq(appId).and(predicate == null ? TRUE : predicate));
    }
  }
}
