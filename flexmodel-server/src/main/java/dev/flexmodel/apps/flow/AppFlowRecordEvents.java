package dev.flexmodel.apps.flow;

import dev.flexmodel.apps.AppService;
import dev.flexmodel.common.SessionContext;
import dev.flexmodel.common.messaging.*;
import dev.flexmodel.project.ProjectService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.*;

import static dev.flexmodel.common.Values.*;

/**
 * Apps-only change capture; publishing an event is part of the business transaction.
 */
@ApplicationScoped
public class AppFlowRecordEvents implements ReliableMessageHandler {
  private static final Set<String> AUDIT = Set.of("updated_at", "updated_by", "created_at", "created_by");
  @Inject
  ReliableMessages messages;
  @Inject
  ProjectService projects;
  @Inject
  SessionContext current;
  @Inject
  AppFlowBindings bindings;
  @Inject
  AppFlowRuntime runtime;

  public void capture(String app, String form, Map<String, Object> before, Map<String, Object> after) {
    if (AppService.importBypassed() || AppFlowRecordContext.runId() != null) return;
    Map<String, Object> old = before == null ? Map.of() : AppFlowJson.copy(before), next = after == null ? Map.of() : AppFlowJson.copy(after);
    var fields = new LinkedHashSet<>(old.keySet());
    fields.addAll(next.keySet());
    fields.removeIf(k -> AUDIT.contains(k) || Objects.equals(old.get(k), next.get(k)));
    if (before != null && after != null && fields.isEmpty()) return;
    String id = UUID.randomUUID().toString(), record = Objects.toString((after == null ? before : after).get("id"), "");
    var payload = new LinkedHashMap<String, Object>();
    payload.putAll(Map.of("eventId", id, "formName", form, "recordId", record, "operation", before == null ? "CREATE" : after == null ? "DELETE" : "UPDATE",
      "changedFields", fields, "occurredAt", Instant.now().toString(), "actor", current.isAuthenticatedUser() ? Objects.toString(current.getUserId(), "") : "",
      "principal", Objects.toString(current.getUserId(), ""), "orderingKey", "record:" + form + ":" + record));
    payload.put("before", before == null ? null : old);
    payload.put("after", after == null ? null : next);
    messages.enqueueBusiness(app, projects.resolveDatabaseName(app), "record-event:" + id, type(), payload);
  }

  public String type() {
    return "flow.record.changed";
  }

  public void handle(String app, String message, Map<String, Object> payload) {
    for (var binding : bindings.active(app)) {
      var cfg = object(binding.getConfig());
      if (!"DATA_EVENT".equals(text(cfg, "type")) || !text(cfg, "formName").equals(text(payload, "formName"))
        || !strings(cfg.get("operations")).contains(text(payload, "operation"))) continue;
      if ("UPDATE".equals(text(payload, "operation")) && !strings(cfg.get("watchedFields")).isEmpty()
        && Collections.disjoint(strings(cfg.get("watchedFields")), strings(payload.get("changedFields")))) continue;
      var snapshot = object(payload.get(payload.get("after") == null ? "before" : "after"));
      var event = new LinkedHashMap<>(payload);
      event.put("type", "DATA_EVENT");
      var variables = new LinkedHashMap<>(snapshot);
      variables.put("record", snapshot);
      variables.put("trigger", event);
      if (!AppFlowRecordActions.matches(object(cfg.get("conditions")), variables)) continue;
      runtime.automatic(app, binding, "event:" + text(payload, "eventId"), event, snapshot);
    }
  }
}
