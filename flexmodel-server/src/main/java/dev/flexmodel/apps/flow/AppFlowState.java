package dev.flexmodel.apps.flow;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.codegen.entity.AppFlowInstance;
import dev.flexmodel.common.ProjectUnitOfWork;
import dev.flexmodel.common.messaging.RuntimeMessageRepository;
import dev.flexmodel.reflect.ReflectionUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;

import static dev.flexmodel.apps.flow.AppFlowJson.*;
import static dev.flexmodel.common.Values.*;

/**
 * Business views over Flow's existing instance data, node instances and execution log.
 */
@ApplicationScoped
public class AppFlowState {
  private static final Logger LOGGER = LoggerFactory.getLogger(AppFlowState.class);
  private static final Set<String> FINISHED = Set.of("SUCCEEDED", "REJECTED", "TERMINATED");
  @Inject
  AppFlowInstanceRepository appFlowInstanceRepository;
  @Inject
  AppFlowInstanceDataRepository appFlowInstanceDataRepository;
  @Inject
  AppFlowNodeInstanceRepository appFlowNodeInstanceRepository;
  @Inject
  AppFlowNodeInstanceLogRepository appFlowNodeInstanceLogRepository;
  @Inject
  AppFlowDeploymentRepository appFlowDeploymentRepository;
  @Inject
  RuntimeMessageRepository runtimeMessageRepository;

  public Map<String, Object> run(String app, String id) {
    var run = appFlowInstanceRepository.get(app, id);
    if ("ACTIVE".equals(text(run, "status")))
      run.put("status", text(run, "flow_instance_id").isEmpty() ? "SUBMITTING" : "RUNNING");
    return run;
  }

  public Map<String, Object> variables(String app, Map<String, Object> run) {
    var rows = appFlowInstanceDataRepository.findByFlowInstance(app, text(run, "flow_instance_id"));
    if (!rows.isEmpty()) {
      var latest = rows.stream().max(Comparator.comparingLong(row -> ((Number) row.get("id")).longValue())).orElseThrow();
      return JsonUtils.parseToMap(text(latest, "instance_data"));
    }
    var starts = runtimeMessageRepository.findByMessageId(app, "start:" + text(run, "id"));
    return starts.isEmpty() ? new LinkedHashMap<>() : new LinkedHashMap<>(object(object(starts.getFirst().get("payload")).get("variables")));
  }

  public Map<String, Object> ending(String app, Map<String, Object> run) {
    if (!FINISHED.contains(text(run, "status")) || text(run, "flow_deploy_id").isBlank()) return null;
    try {
      var reached = appFlowNodeInstanceRepository.findByFlowInstance(app, text(run, "flow_instance_id")).stream()
        .map(row -> text(row, "node_key")).toList();
      var ending = AppFlowForecast.ending(appFlowDeploymentRepository.deployedFlow(app, text(run, "flow_deploy_id")), reached);
      if (ending != null) ending.put("status", text(run, "status"));
      return ending;
    } catch (RuntimeException error) {
      LOGGER.warn("审批结束节点读取失败.||runId={}", text(run, "id"), error);
      return null;
    }
  }

  public List<Map<String, Object>> upcoming(String app, Map<String, Object> run, List<Map<String, Object>> waits) {
    var current = waits.stream().filter(wait -> "WAITING".equals(text(wait, "status")))
      .map(wait -> text(wait, "node_key")).filter(key -> !key.isBlank()).distinct().toList();
    if (current.isEmpty() || text(run, "flow_deploy_id").isBlank()) return List.of();
    try {
      return AppFlowForecast.forecast(appFlowDeploymentRepository.deployedFlow(app, text(run, "flow_deploy_id")), current, variables(app, run));
    } catch (RuntimeException error) {
      LOGGER.warn("审批节点预测失败.||runId={}", text(run, "id"), error);
      return List.of();
    }
  }

  private void storeVariables(String app, Map<String, Object> run, String nodeId, String nodeKey, String key, Map<String, Object> variables) {
    var present = appFlowInstanceDataRepository.findByDataKey(app, key);
    if (!present.isEmpty()) return;
    var data = new LinkedHashMap<String, Object>();
    data.putAll(Map.of("node_instance_id", nodeId, "flow_instance_id", text(run, "flow_instance_id"), "instance_data_id", key,
      "flow_deploy_id", text(run, "flow_deploy_id"), "flow_module_id", text(run, "flow_id"), "node_key", nodeKey,
      "instance_data", JsonUtils.toJsonString(variables), "type", 4, "archive", 0));
    appFlowInstanceDataRepository.insert(app, data);
  }

  public boolean completed(String app, String nodeId) {
    return appFlowNodeInstanceLogRepository.existsByEventKey(app, "approval-node:" + nodeId);
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
    if (appFlowNodeInstanceLogRepository.existsByEventKey(app, event))
      return;
    var log = new LinkedHashMap<String, Object>();
    log.putAll(Map.of("node_instance_id", nodeId, "flow_instance_id", instanceId,
      "instance_data_id", dataId, "node_key", nodeKey, "type", 2, "status", 1, "archive", 0,
      "event_key", event, "attributes", attributes, "created_at", created));
    appFlowNodeInstanceLogRepository.insert(app, log);
  }

  public List<Map<String, Object>> logs(String app, Map<String, Object> run) {
    return appFlowNodeInstanceLogRepository.findByFlowInstance(app, text(run, "flow_instance_id")).stream()
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
    var flow = appFlowDeploymentRepository.deployedFlow(app, text(run, "flow_deploy_id"));
    var node = flatten(objects(object(flow.get("definition")).get("nodes"))).stream().filter(value -> key.equals(text(value, "id"))).findFirst().orElseThrow();
    var view = new LinkedHashMap<>(wait);
    view.putAll(Map.of("node_key", key, "title", text(object(node.get("data")), "title"), "sign_mode", text(config(node), "signMode")));
    return view;
  }

  public Map<String, Object> nodeInstance(String app, String id) {
    return appFlowNodeInstanceRepository.getByNodeInstanceId(app, id);
  }

  public Map<String, Object> parent(String app, Map<String, Object> run) {
    if (text(run, "parent_task_id").isEmpty()) return null;
    String instance = text(nodeInstance(app, text(run, "parent_task_id")), "flow_instance_id");
    return appFlowInstanceRepository.findByFlowInstanceId(app, instance).stream().findFirst().orElseThrow();
  }

  public List<Map<String, Object>> children(String app, Map<String, Object> run) {
    var nodes = appFlowNodeInstanceRepository.findByFlowInstance(app, text(run, "flow_instance_id"))
      .stream().map(row -> text(row, "node_instance_id")).toList();
    return appFlowInstanceRepository.findByParentTaskIds(app, nodes);
  }

  public String rootId(String app, Map<String, Object> run) {
    Map<String, Object> root = run, parent;
    while ((parent = parent(app, root)) != null) root = parent;
    return text(root, "id");
  }

  public Map<String, Object> lock(String app, String runId, String rootId) {
    ProjectUnitOfWork.lock(app, ReflectionUtils.getModelNameFromClass(AppFlowInstance.class), "id", rootId);
    return run(app, runId);
  }
}
