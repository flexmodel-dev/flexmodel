package dev.flexmodel.approval;

import dev.flexmodel.apps.AppFormService;
import dev.flexmodel.apps.AppRecordService;
import dev.flexmodel.common.ValidationException;
import dev.flexmodel.project.ProjectService;
import dev.flexmodel.query.*;
import dev.flexmodel.session.Session;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.*;

import static dev.flexmodel.approval.ApprovalJson.*;
import static dev.flexmodel.common.Values.*;
import static dev.flexmodel.query.Expressions.*;

/**
 * Application metadata is resolved on the server. No datasource or raw filter is accepted from nodes.
 */
@ApplicationScoped
public class ApprovalRecordActions {
  @Inject
  AppFormService forms;
  @Inject
  AppRecordService records;
  @Inject
  ProjectService projects;
  @Inject
  dev.flexmodel.common.persistence.ServerSessions sessions;

  public String schema(String appId, String form) {
    var metadata = forms.findForm(appId, form);
    if (metadata.builtin()) throw new ValidationException("审批流程仅支持应用业务表单");
    return projects.resolveDatabaseName(appId);
  }

  public Map<String, Object> snapshot(String appId, String form, String id) {
    try (Session session = sessions.open(schema(appId, form))) {
      return session.data().findById(form, id).orElseThrow(() -> new ValidationException("提交记录不存在"));
    }
  }

  public Object execute(String appId, Map<String, Object> cfg, Map<String, Object> variables) {
    String kind = text(cfg, "kind"), form = text(object(cfg.get("form")), "name");
    String schema = schema(appId, form);
    Map<String, Object> assignments = new LinkedHashMap<>();
    for (var assignment : objects(cfg.get("assignments")))
      assignments.put(text(assignment, "field"), value(object(assignment.get("value")), variables));
    if (kind.equals("create")) {
      var created = records.createRecord(appId, form, assignments, null, null, null);
      return snapshot(appId, form, text(created, "id"));
    }
    Predicate filter = predicate(objects(cfg.get("conditions")), text(cfg, "match"), variables);
    try (Session session = sessions.open(schema)) {
      List<Map<String, Object>> selected = session.dsl().selectFrom(form).where(filter)
        .limit(kind.equals("query") && text(cfg, "queryMode").equals("one") ? 1 : 1001).execute();
      if (selected.size() > 1000) throw new ValidationException("单个节点最多处理 1000 条记录，请收紧筛选条件");
      if (kind.equals("query"))
        return text(cfg, "queryMode").equals("one") ? selected.stream().findFirst().orElse(null) : selected;
      List<Map<String, Object>> changed = new ArrayList<>();
      for (var record : selected) {
        String id = text(record, "id");
        if (kind.equals("update")) {
          records.updateRecord(appId, form, id, assignments, null, null, null);
          changed.add(snapshot(appId, form, id));
        } else {
          records.deleteRecord(appId, form, id, null, null, null);
          changed.add(record);
        }
      }
      return Map.of("affectedCount", changed.size(), "records", changed);
    }
  }

  public static Predicate predicate(List<Map<String, Object>> conditions, String match, Map<String, Object> variables) {
    Predicate result = null;
    for (var condition : conditions) {
      var field = field(text(condition, "field"));
      Object right = value(object(condition.get("value")), variables);
      Predicate next = ConditionOperator.parse(text(condition, "operator")).test(field, right);
      result = result == null ? next : match.equals("any") ? result.or(next) : result.and(next);
    }
    return result == null ? TRUE : result;
  }

  public static boolean matches(Map<String, Object> cfg, Map<String, Object> variables) {
    List<Boolean> results = objects(cfg.get("conditions")).stream().map(condition -> {
      Object left = path(variables, text(condition, "field")), right = value(object(condition.get("value")), variables);
      return ConditionOperator.parse(text(condition, "operator")).apply(left, right);
    }).toList();
    return text(cfg, "match").equals("any") ? results.stream().anyMatch(Boolean::booleanValue) : results.stream().allMatch(Boolean::booleanValue);
  }
}
