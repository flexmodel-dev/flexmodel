package dev.flexmodel.approval;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.common.ProjectUnitOfWork;
import dev.flexmodel.session.Session;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDateTime;
import java.util.*;

import static dev.flexmodel.approval.ApprovalJson.*;
import static dev.flexmodel.query.Expressions.*;

/**
 * Business views over Flow's existing instance data, node instances and execution log.
 */
@ApplicationScoped
public class ApprovalFlowState {
  @Inject
  ApprovalRepository repository;

  public Map<String, Object> run(String app, String id) {
    var run = repository.get(app, "f_approval_instance", id);
    if ("ACTIVE".equals(text(run, "status")))
      run.put("status", text(run, "flow_instance_id").isEmpty() ? "SUBMITTING" : "RUNNING");
    return run;
  }

  public Map<String, Object> variables(String app, Map<String, Object> run) {
    try (var session = repository.open(app)) {
      var rows = session.dsl().selectFrom("f_ei_instance_data").where(field("flow_instance_id").eq(text(run, "flow_instance_id")))
        .orderByDesc("id").limit(1).execute();
      if (!rows.isEmpty()) return JsonUtils.parseToMap(text(rows.getFirst(), "instance_data"));
      var starts = repository.list(app, "f_runtime_message", field("message_id").eq("start:" + text(run, "id")));
      return starts.isEmpty() ? new LinkedHashMap<>() : new LinkedHashMap<>(object(object(starts.getFirst().get("payload")).get("variables")));
    }
  }

  private static void storeVariables(Session session, Map<String, Object> run, String nodeId, String nodeKey, String key, Map<String, Object> variables) {
    var present = session.dsl().selectFrom("f_ei_instance_data").where(field("instance_data_id").eq(key)).execute();
    if (!present.isEmpty()) return;
    var data = new LinkedHashMap<String, Object>();
    data.putAll(Map.of("node_instance_id", nodeId, "flow_instance_id", text(run, "flow_instance_id"), "instance_data_id", key,
      "flow_deploy_id", text(run, "flow_deploy_id"), "flow_module_id", text(run, "flow_id"), "node_key", nodeKey,
      "instance_data", JsonUtils.toJsonString(variables), "type", 4, "archive", 0));
    session.data().insert("f_ei_instance_data", data);
  }

  public boolean completed(String app, String nodeId) {
    return !repository.list(app, "f_ei_node_instance_log", field("event_key").eq("approval-node:" + nodeId)).isEmpty();
  }

  public void log(String app, Map<String, Object> run, String nodeId, Map<String, Object> node,
                  Map<String, Object> input, Object output, Map<String, Object> variables) {
    try (var session = repository.open(app)) {
      String key = "approval-data:" + nodeId;
      storeVariables(session, run, nodeId, text(node, "id"), key, variables);
      Map<String, Object> attributes = new LinkedHashMap<>(Map.of("run_id", text(run, "id"), "title", text(object(node.get("data")), "title"),
        "kind", text(node, "type"), "input", input));
      attributes.put("output", output);
      storeLog(session, text(run, "flow_instance_id"), nodeId, text(node, "id"), key, attributes, LocalDateTime.now());
    }
  }

  private static void storeLog(Session session, String instanceId, String nodeId, String nodeKey, String dataId,
                               Map<String, Object> attributes, Object created) {
    String event = "approval-node:" + nodeId;
    if (!session.dsl().selectFrom("f_ei_node_instance_log").where(field("event_key").eq(event)).execute().isEmpty())
      return;
    session.data().insert("f_ei_node_instance_log", new LinkedHashMap<>(Map.of("node_instance_id", nodeId, "flow_instance_id", instanceId,
      "instance_data_id", dataId, "node_key", nodeKey, "type", 2, "status", 1, "archive", 0,
      "event_key", event, "attributes", attributes, "created_at", created)));
  }

  public List<Map<String, Object>> logs(String app, Map<String, Object> run) {
    return repository.list(app, "f_ei_node_instance_log", field("flow_instance_id").eq(text(run, "flow_instance_id"))).stream()
      .filter(row -> text(object(row.get("attributes")), "run_id").equals(text(run, "id")))
      .map(row -> {
        var view = new LinkedHashMap<>(object(row.get("attributes")));
        view.putAll(Map.of("id", text(row, "node_instance_id"), "node_key", text(row, "node_key"), "created_at", row.get("created_at")));
        return (Map<String, Object>) view;
      }).toList();
  }

  public Map<String, Object> waitView(String app, Map<String, Object> wait) {
    var run = run(app, text(wait, "run_id"));
    var nativeNode = nodeInstance(app, text(wait, "id"));
    String key = text(nativeNode, "node_key");
    var flow = repository.deployedFlow(app, text(run, "flow_deploy_id"));
    var node = flatten(objects(object(flow.get("definition")).get("nodes"))).stream().filter(value -> key.equals(text(value, "id"))).findFirst().orElseThrow();
    var view = new LinkedHashMap<>(wait);
    view.putAll(Map.of("node_key", key, "title", text(object(node.get("data")), "title"), "sign_mode", text(config(node), "signMode")));
    return view;
  }

  public Map<String, Object> nodeInstance(String app, String id) {
    return repository.list(app, "f_ei_node_instance", field("node_instance_id").eq(id)).stream().findFirst().orElseThrow();
  }

  public Map<String, Object> parent(String app, Map<String, Object> run) {
    if (text(run, "parent_task_id").isEmpty()) return null;
    String instance = text(nodeInstance(app, text(run, "parent_task_id")), "flow_instance_id");
    return repository.list(app, "f_approval_instance", field("flow_instance_id").eq(instance)).stream().findFirst().orElseThrow();
  }

  public List<Map<String, Object>> children(String app, Map<String, Object> run) {
    var nodes = repository.list(app, "f_ei_node_instance", field("flow_instance_id").eq(text(run, "flow_instance_id")))
      .stream().map(row -> text(row, "node_instance_id")).toList();
    return nodes.isEmpty() ? List.of() : repository.list(app, "f_approval_instance", field("parent_task_id").in(nodes.toArray()));
  }

  public String rootId(String app, Map<String, Object> run) {
    Map<String, Object> root = run, parent;
    while ((parent = parent(app, root)) != null) root = parent;
    return text(root, "id");
  }

  public Map<String, Object> lock(String app, String runId, String rootId) {
    ProjectUnitOfWork.lock(app, "f_approval_instance", "id", rootId);
    return run(app, runId);
  }
}
