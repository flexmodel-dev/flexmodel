package dev.flexmodel.approval;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.codegen.entity.ApprovalInstance;
import dev.flexmodel.codegen.entity.InstanceData;
import dev.flexmodel.codegen.entity.NodeInstance;
import dev.flexmodel.codegen.entity.NodeInstanceLog;
import dev.flexmodel.codegen.entity.RuntimeMessage;
import dev.flexmodel.common.ProjectUnitOfWork;
import dev.flexmodel.common.messaging.RuntimeMessageRepository;
import dev.flexmodel.reflect.ReflectionUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;

import static dev.flexmodel.approval.ApprovalJson.*;
import static dev.flexmodel.common.Values.*;
import static dev.flexmodel.codegen.System.approvalInstance;
import static dev.flexmodel.codegen.System.instanceData;
import static dev.flexmodel.codegen.System.nodeInstance;
import static dev.flexmodel.codegen.System.nodeInstanceLog;
import static dev.flexmodel.codegen.System.runtimeMessage;
import static dev.flexmodel.query.Expressions.*;

/**
 * Business views over Flow's existing instance data, node instances and execution log.
 */
@ApplicationScoped
public class ApprovalFlowState {
  private static final Logger LOGGER = LoggerFactory.getLogger(ApprovalFlowState.class);
  @Inject
  ApprovalInstanceRepository approvalInstanceRepository;
  @Inject
  ApprovalInstanceDataRepository approvalInstanceDataRepository;
  @Inject
  ApprovalNodeInstanceRepository approvalNodeInstanceRepository;
  @Inject
  ApprovalNodeInstanceLogRepository approvalNodeInstanceLogRepository;
  @Inject
  ApprovalFlowDeploymentRepository approvalFlowDeploymentRepository;
  @Inject
  RuntimeMessageRepository runtimeMessageRepository;

  public Map<String, Object> run(String app, String id) {
    var run = approvalInstanceRepository.get(app, id);
    if ("ACTIVE".equals(text(run, "status")))
      run.put("status", text(run, "flow_instance_id").isEmpty() ? "SUBMITTING" : "RUNNING");
    return run;
  }

  public Map<String, Object> variables(String app, Map<String, Object> run) {
    var rows = approvalInstanceDataRepository.findByFlowInstance(app, text(run, "flow_instance_id"));
    if (!rows.isEmpty()) {
      var latest = rows.stream().max(Comparator.comparingLong(row -> ((Number) row.get("id")).longValue())).orElseThrow();
      return JsonUtils.parseToMap(text(latest, "instance_data"));
    }
    var starts = runtimeMessageRepository.findByMessageId(app, "start:" + text(run, "id"));
    return starts.isEmpty() ? new LinkedHashMap<>() : new LinkedHashMap<>(object(object(starts.getFirst().get("payload")).get("variables")));
  }

  public List<Map<String, Object>> upcoming(String app, Map<String, Object> run, List<Map<String, Object>> waits) {
    var current = waits.stream().filter(wait -> "WAITING".equals(text(wait, "status")))
      .map(wait -> text(wait, "node_key")).filter(key -> !key.isBlank()).distinct().toList();
    if (current.isEmpty() || text(run, "flow_deploy_id").isBlank()) return List.of();
    try {
      return ApprovalForecast.forecast(approvalFlowDeploymentRepository.deployedFlow(app, text(run, "flow_deploy_id")), current, variables(app, run));
    } catch (RuntimeException error) {
      LOGGER.warn("审批节点预测失败.||runId={}", text(run, "id"), error);
      return List.of();
    }
  }

  private void storeVariables(String app, Map<String, Object> run, String nodeId, String nodeKey, String key, Map<String, Object> variables) {
    var present = approvalInstanceDataRepository.findByDataKey(app, key);
    if (!present.isEmpty()) return;
    var data = new LinkedHashMap<String, Object>();
    data.putAll(Map.of("node_instance_id", nodeId, "flow_instance_id", text(run, "flow_instance_id"), "instance_data_id", key,
      "flow_deploy_id", text(run, "flow_deploy_id"), "flow_module_id", text(run, "flow_id"), "node_key", nodeKey,
      "instance_data", JsonUtils.toJsonString(variables), "type", 4, "archive", 0));
    approvalInstanceDataRepository.insert(app, data);
  }

  public boolean completed(String app, String nodeId) {
    return approvalNodeInstanceLogRepository.existsByEventKey(app, "approval-node:" + nodeId);
  }

  public void log(String app, Map<String, Object> run, String nodeId, Map<String, Object> node,
                  Map<String, Object> input, Object output, Map<String, Object> variables) {
    String key = "approval-data:" + nodeId;
    storeVariables(app, run, nodeId, text(node, "id"), key, variables);
    Map<String, Object> attributes = new LinkedHashMap<>(Map.of("run_id", text(run, "id"), "title", text(object(node.get("data")), "title"),
      "kind", text(node, "type"), "input", input));
    attributes.put("output", output);
    storeLog(app, text(run, "flow_instance_id"), nodeId, text(node, "id"), key, attributes, LocalDateTime.now());
  }

  private void storeLog(String app, String instanceId, String nodeId, String nodeKey, String dataId,
                        Map<String, Object> attributes, Object created) {
    String event = "approval-node:" + nodeId;
    if (approvalNodeInstanceLogRepository.existsByEventKey(app, event))
      return;
    var log = new LinkedHashMap<String, Object>();
    log.putAll(Map.of("node_instance_id", nodeId, "flow_instance_id", instanceId,
      "instance_data_id", dataId, "node_key", nodeKey, "type", 2, "status", 1, "archive", 0,
      "event_key", event, "attributes", attributes, "created_at", created));
    approvalNodeInstanceLogRepository.insert(app, log);
  }

  public List<Map<String, Object>> logs(String app, Map<String, Object> run) {
    return approvalNodeInstanceLogRepository.findByFlowInstance(app, text(run, "flow_instance_id")).stream()
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
    var flow = approvalFlowDeploymentRepository.deployedFlow(app, text(run, "flow_deploy_id"));
    var node = flatten(objects(object(flow.get("definition")).get("nodes"))).stream().filter(value -> key.equals(text(value, "id"))).findFirst().orElseThrow();
    var view = new LinkedHashMap<>(wait);
    view.putAll(Map.of("node_key", key, "title", text(object(node.get("data")), "title"), "sign_mode", text(config(node), "signMode")));
    return view;
  }

  public Map<String, Object> nodeInstance(String app, String id) {
    return approvalNodeInstanceRepository.getByNodeInstanceId(app, id);
  }

  public Map<String, Object> parent(String app, Map<String, Object> run) {
    if (text(run, "parent_task_id").isEmpty()) return null;
    String instance = text(nodeInstance(app, text(run, "parent_task_id")), "flow_instance_id");
    return approvalInstanceRepository.findByFlowInstanceId(app, instance).stream().findFirst().orElseThrow();
  }

  public List<Map<String, Object>> children(String app, Map<String, Object> run) {
    var nodes = approvalNodeInstanceRepository.findByFlowInstance(app, text(run, "flow_instance_id"))
      .stream().map(row -> text(row, "node_instance_id")).toList();
    return approvalInstanceRepository.findByParentTaskIds(app, nodes);
  }

  public String rootId(String app, Map<String, Object> run) {
    Map<String, Object> root = run, parent;
    while ((parent = parent(app, root)) != null) root = parent;
    return text(root, "id");
  }

  public Map<String, Object> lock(String app, String runId, String rootId) {
    ProjectUnitOfWork.lock(app, ReflectionUtils.getModelNameFromClass(ApprovalInstance.class), "id", rootId);
    return run(app, runId);
  }
}
