package dev.flexmodel.apps.flow;

import dev.flexmodel.common.ProjectUnitOfWork;
import dev.flexmodel.common.SessionContext;
import dev.flexmodel.common.SqlErrors;
import dev.flexmodel.common.ValidationException;
import dev.flexmodel.common.messaging.ReliableMessages;
import dev.flexmodel.common.messaging.RuntimeMessageRepository;
import dev.flexmodel.directory.DirectoryService;
import dev.flexmodel.flow.event.DurableFlowListener;
import dev.flexmodel.notification.NotificationService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ClientErrorException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.TreeMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static dev.flexmodel.apps.flow.AppFlowJson.*;
import static dev.flexmodel.apps.record.AppRecordHistoryContext.Snapshot;
import static dev.flexmodel.common.Values.*;

/**
 * Form integration reacts to Flow events; Flow never calls the form or approval services.
 */
@ApplicationScoped
public class AppFlowRuntime implements DurableFlowListener {

  @Inject
  NotificationService notificationService;
  @Inject
  AppFlowInstanceRepository appFlowInstanceRepository;
  @Inject
  AppApprovalTaskRepository appApprovalTaskRepository;
  @Inject
  AppFlowRecordLockRepository appFlowRecordLockRepository;
  @Inject
  AppFlowDefinitionRepository appFlowDefinitionRepository;
  @Inject
  AppFlowDeploymentRepository appFlowDeploymentRepository;
  @Inject
  RuntimeMessageRepository runtimeMessageRepository;
  @Inject
  AppFlowState state;
  @Inject
  ProjectUnitOfWork transactions;
  @Inject
  AppFlowService appFlowService;
  @Inject
  AppFlowRecordActions records;
  @Inject
  DirectoryService directoryService;
  @Inject
  ReliableMessages messages;
  @Inject
  SessionContext current;
  @Inject
  AppFlowBindings bindings;
  @Inject
  AppFlowDefinitionRepository definitions;
  @Inject
  dev.flexmodel.apps.record.AppRecordHistoryRecorder history;
  @Inject
  dev.flexmodel.apps.record.AppRecordHistoryContext historyContext;

  public Map<String, Object> manual(String app, String flow, String record, String key, Map<String, Object> input) {
    if (key == null || key.isBlank() || key.length() > 120) throw new ValidationException("发起必须携带有效幂等键");
    String principal = current.getUserId();
    if (principal == null || principal.isBlank())
      throw new jakarta.ws.rs.ForbiddenException("请登录或使用具有执行权限的 API Key");
    if (record == null || record.isBlank()) throw new ValidationException("请选择主表单记录");
    String requestKey = principal + ":" + key;
    String hash = digest(Map.of("flow", flow, "record", record, "input", input));
    var prior = appFlowInstanceRepository.findByRequestKey(app, requestKey);
    if (!prior.isEmpty()) return reuse(app, prior.getFirst(), hash);
    var binding = bindings.find(app, flow);
    if (binding == null || !Boolean.TRUE.equals(binding.getState()))
      throw new ValidationException("流程尚未发布或已停用");
    var deployed = appFlowDeploymentRepository.deployedFlow(app, binding.getDeploymentId());
    var cfg = FlowTrigger.of(deployed);
    if (!"MANUAL".equals(text(cfg, "type"))) throw new ValidationException("只有手动触发流程可通过此接口发起");
    String form = text(cfg, "formName");
    if (record == null || record.isBlank()) throw new ValidationException("请选择主表单记录");
    try {
      return transactions.execute(app, () -> {
        ProjectUnitOfWork.lock(app, form, "id", record);
        var snapshot = records.snapshot(app, form, record);
        return transactions.executePlatform(app, () -> {
          definitions.lock(app, flow);
          var latest = bindings.find(app, flow);
          if (latest == null || !Boolean.TRUE.equals(latest.getState()) || !Objects.equals(latest.getDeploymentId(), binding.getDeploymentId()))
            throw new jakarta.ws.rs.ClientErrorException("流程运行版本已变化，请重试", 409);
          var existing = appFlowInstanceRepository.findByRequestKey(app, requestKey);
          if (!existing.isEmpty()) return reuse(app, existing.getFirst(), hash);
          String id = UUID.randomUUID().toString();
          if (Boolean.TRUE.equals(cfg.get("lockRecord"))) {
            String schema = records.schema(app, form);
            if (!appFlowRecordLockRepository.findByRecord(app, schema, form, record).isEmpty())
              throw new jakarta.ws.rs.ClientErrorException("记录已被其他流程锁定", 409);
            appFlowRecordLockRepository.insert(app, Map.of("id", id, "run_id", id, "schema_name", schema, "model_name", form, "record_id", record));
          }
          return accept(app, id, requestKey, hash, flow, binding.getDeploymentId(), form, record,
            current.isAuthenticatedUser() ? principal : "", principal, "MANUAL", snapshot, input, Map.of("type", "MANUAL"));
        });
      });
    } catch (RuntimeException error) {
      if (!SqlErrors.constraintViolation(error)) throw error;
      var existing = appFlowInstanceRepository.findByRequestKey(app, requestKey);
      if (existing.isEmpty()) throw error;
      return reuse(app, existing.getFirst(), hash);
    }
  }

  private Map<String, Object> reuse(String app, Map<String, Object> run, String hash) {
    if (!hash.equals(text(run, "request_hash")))
      throw new jakarta.ws.rs.ClientErrorException("幂等键已用于其他流程请求", 409);
    return state.run(app, text(run, "id"));
  }

  public Map<String, Object> automatic(String app, dev.flexmodel.codegen.entity.Trigger expected, String source, Map<String, Object> trigger, Map<String, Object> snapshot) {
    String flow = expected.getJobId();
    return transactions.executePlatform(app, () -> {
      definitions.lock(app, flow);
      var old = appFlowInstanceRepository.findByRequestKey(app, source + ":" + flow);
      if (!old.isEmpty()) return state.run(app, text(old.getFirst(), "id"));
      var binding = bindings.find(app, flow);
      if (binding == null || !Boolean.TRUE.equals(binding.getState()) || !Objects.equals(binding.getGeneration(), expected.getGeneration())) {
        bindings.log(app, flow, source, "SKIPPED_STALE", "", "触发绑定已变化或停用");
        return Map.of();
      }
      boolean scheduled = "SCHEDULE".equals(text(trigger, "type"));
      if (scheduled && appFlowInstanceRepository.findAll(app).stream().anyMatch(r -> flow.equals(text(r, "flow_id")) && text(r, "parent_task_id").isBlank() && !Set.of("SUCCEEDED", "REJECTED", "TERMINATED").contains(text(r, "status")))) {
        bindings.log(app, flow, source, "SKIPPED_OVERLAP", "", "上一轮流程尚未结束");
        return Map.of();
      }
      var cfg = FlowTrigger.of(appFlowDeploymentRepository.deployedFlow(app, binding.getDeploymentId()));
      String actor = scheduled ? "" : text(trigger, "actor");
      String principal = scheduled ? "system:flow:" + flow : text(trigger, "principal");
      String id = UUID.randomUUID().toString();
      var result = accept(app, id, source + ":" + flow, digest(trigger), flow, binding.getDeploymentId(), text(cfg, "formName"), text(trigger, "recordId"), actor, principal, text(trigger, "type"), snapshot, Map.of(), trigger);
      bindings.log(app, flow, source, "ACCEPTED", id, "");
      return result;
    });
  }

  private Map<String, Object> accept(String app, String id, String key, String hash, String flow, String deployment, String form, String record, String actor, String principal, String type, Map<String, Object> snapshot, Map<String, Object> input, Map<String, Object> trigger) {
    var run = run(id, key, flow, deployment, form, record, actor, snapshot);
    run.putAll(Map.of("principal", principal, "trigger_type", type, "source_id", key, "request_hash", hash));
    var vars = new LinkedHashMap<String, Object>();
    vars.putAll(Map.of("input", input, "trigger", trigger, "context", Map.of("instanceId", id, "rootInstanceId", id, "principal", principal, "initiator", actor), "nodes", Map.of(), "nodeMembers", Map.of(), "__runId", id, "__rootRunId", id));
    if (!type.equals("SCHEDULE")) vars.put("record", snapshot);
    checkSize(vars);
    appFlowInstanceRepository.insert(app, run);
    start(app, run, vars);
    if (!form.isBlank())
      ProjectUnitOfWork.afterCommit(() -> history.flowStarted(app, form, record, text(appFlowDeploymentRepository.get(app, deployment), "flow_name"), actor, id));
    return state.run(app, id);
  }

  private Map<String, Object> run(String id, String key, String flow, String deployment, String form, String record, String actor, Map<String, Object> snapshot) {
    var row = new LinkedHashMap<String, Object>();
    row.putAll(Map.ofEntries(Map.entry("id", id), Map.entry("request_key", key), Map.entry("flow_id", flow), Map.entry("flow_deploy_id", deployment), Map.entry("initiator", actor), Map.entry("status", "ACTIVE"), Map.entry("snapshot", snapshot), Map.entry("created_at", LocalDateTime.now()), Map.entry("updated_at", LocalDateTime.now()), Map.entry("principal", actor), Map.entry("trigger_type", "SUBFLOW"), Map.entry("source_id", key), Map.entry("request_hash", key)));
    if (!form.isBlank()) row.put("form_name", form);
    if (!record.isBlank()) row.put("record_id", record);
    return row;
  }

  private static Object canonical(Object value) {
    if (value instanceof Map<?, ?> map) {
      var sorted = new TreeMap<String, Object>();
      map.forEach((k, v) -> sorted.put(k.toString(), canonical(v)));
      return sorted;
    }
    if (value instanceof java.util.List<?> list) return list.stream().map(AppFlowRuntime::canonical).toList();
    return value;
  }

  private static String digest(Object value) {
    try {
      return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(dev.flexmodel.JsonUtils.toJsonString(canonical(value)).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private void start(String appId, Map<String, Object> run, Map<String, Object> variables) {
    messages.enqueue(appId, "start:" + text(run, "id"), "flow.command", Map.of("action", "start", "handler", "app-flow", "deploymentId", text(run, "flow_deploy_id"), "initiator", text(run, "initiator"), "principal", text(run, "principal"), "variables", variables));
  }

  public void complete(String appId, Map<String, Object> run, String taskId, Map<String, Object> variables) {
    checkSize(variables);
    messages.enqueue(appId, "complete:" + taskId, "flow.command", Map.of("action", "complete", "handler", "app-flow", "instanceId", text(run, "flow_instance_id"), "taskId", taskId, "principal", text(run, "principal"), "variables", variables, "orderingKey", state.rootId(appId, run)));
  }

  private void checkSize(Map<String, Object> variables) {
    if (dev.flexmodel.JsonUtils.toJsonString(variables).length() > 900000)
      throw new ValidationException("流程变量超过 900KB，请减少返回字段或记录数量");
  }

  public Map<String, Object> node(String appId, Map<String, Object> run, String nodeId) {
    var flow = appFlowDeploymentRepository.deployedFlow(appId, text(run, "flow_deploy_id"));
    return flatten(objects(object(flow.get("definition")).get("nodes"))).stream()
      .filter(node -> text(node, "id").equals(nodeId)).findFirst().orElseThrow(() -> new ValidationException("发布版本中不存在该节点"));
  }

  public Map<String, Object> eventRun(String appId, Map<String, Object> event) {
    String runId = text(object(event.get("variables")), "__runId");
    if (!runId.isEmpty()) {
      var run = appFlowInstanceRepository.find(appId, runId);
      if (run == null) return null;
      run = state.lock(appId, runId, state.rootId(appId, run));
      if (!text(run, "flow_deploy_id").equals(text(event, "flowDeployId"))) return null;
      if (!text(run, "flow_instance_id").isEmpty() && !text(run, "flow_instance_id").equals(text(event, "flowInstanceId")))
        return null;
      return run;
    }
    var candidates = appFlowInstanceRepository.findByFlowInstanceId(appId, text(event, "flowInstanceId"));
    return candidates.isEmpty() ? null : state.lock(appId, text(candidates.getFirst(), "id"), state.rootId(appId, candidates.getFirst()));
  }

  @Override
  public void onEvent(String appId, String eventId, String routingKey, Map<String, Object> event) {
    Map<String, Object> run = eventRun(appId, event);
    if (run == null) return;
    String runId = text(run, "id"), instanceId = text(event, "flowInstanceId");
    if (routingKey.endsWith("instance.started")) {
      String status = Set.of("SUBMITTING", "RUNNING").contains(text(run, "status")) ? "ACTIVE" : text(run, "status");
      appFlowInstanceRepository.update(appId, runId, Map.of("flow_instance_id", instanceId, "status", status, "updated_at", LocalDateTime.now()));
      return;
    }
    if (text(run, "flow_instance_id").isEmpty()) throw new IllegalStateException("等待启动事件确认后再处理后续事件");
    if (Set.of("SUCCEEDED", "REJECTED", "TERMINATED").contains(text(run, "status"))) return;
    Map<String, Object> variables = new LinkedHashMap<>(object(event.get("variables")));
    if (routingKey.endsWith("instance.completed")) {
      finish(appId, run, "SUCCEEDED", variables);
      return;
    }
    if (routingKey.endsWith("instance.terminated")) {
      finish(appId, run, "REJECTING".equals(text(run, "status")) ? "REJECTED" : "TERMINATED", state.variables(appId, run));
      return;
    }
    if (routingKey.endsWith("instance.failed")) {
      appFlowInstanceRepository.update(appId, runId, Map.of("status", "FAILED", "error", text(event, "errorMessage")));
      return;
    }
    if (!routingKey.equals(dev.flexmodel.flow.event.FlowEventTypes.USER_TASK_SUSPENDED)) return;
    Map<String, Object> attributes = object(event.get("nodeAttributes"));
    if (!"app-flow".equals(text(attributes, "handler"))) return;
    String taskId = text(event, "nodeInstanceId"), nodeKey = text(event, "nodeKey");
    if (state.completed(appId, taskId)) return;
    if (!Set.of("RUNNING", "SUBMITTING").contains(text(run, "status"))) return;
    Map<String, Object> node = node(appId, run, nodeKey), cfg = config(node);
    String kind = text(cfg, "kind");
    if (kind.equals("subflow")) {
      String childId = UUID.nameUUIDFromBytes((appId + ":" + taskId).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
      if (appFlowInstanceRepository.find(appId, childId) != null) return;
      var deployment = appFlowDeploymentRepository.get(appId, text(run, "flow_deploy_id"));
      String childDeploymentId = text(object(AppFlowModel.metadata(deployment).get("children")), nodeKey);
      var childFlow = appFlowDeploymentRepository.deployedFlow(appId, childDeploymentId);
      var childTrigger = FlowTrigger.of(childFlow);
      Map<String, Object> childInput = new LinkedHashMap<>();
      for (var entry : objects(cfg.get("inputs")))
        childInput.put(text(entry, "field"), value(object(entry.get("value")), variables));
      String childForm = text(childTrigger, "formName"), childRecord = "";
      Map<String, Object> childSnapshot = Map.of();
      if (!childForm.isBlank()) {
        childRecord = Objects.toString(childInput.get("recordId"), childForm.equals(text(run, "form_name")) ? text(run, "record_id") : "");
        if (childRecord.isBlank()) throw new ValidationException("子流程需要通过输入 recordId 映射其关联表单记录");
        childSnapshot = records.snapshot(appId, childForm, childRecord);
      }
      Map<String, Object> childVariables = new LinkedHashMap<>();
      childVariables.putAll(Map.of("input", childInput, "trigger", Map.of("type", "SUBFLOW"), "context", Map.of("instanceId", childId, "rootInstanceId", state.rootId(appId, run), "principal", text(run, "principal"), "initiator", text(run, "initiator")), "nodes", Map.of(), "nodeMembers", Map.of(), "__runId", childId, "__rootRunId", state.rootId(appId, run)));
      if (!childForm.isBlank()) childVariables.put("record", childSnapshot);
      var child = run(childId, "child:" + taskId, text(cfg, "flowId"), childDeploymentId, childForm, childRecord, text(run, "initiator"), childSnapshot);
      child.put("principal", text(run, "principal"));
      child.put("parent_task_id", taskId);
      appFlowInstanceRepository.insert(appId, child);
      start(appId, child, childVariables);
      return;
    }
    if (kind.equals("notification")) {
      var output = notificationService.node(appId, runId, taskId, text(run, "initiator"), cfg, variables);
      log(appId, taskId, runId, node, Map.of(), output, variables);
      complete(appId, run, taskId, variables);
      return;
    }
    Map<String, Object> input = copy(variables);
    Object output;
    String actor = current.getUserId();
    String rootRunId = rootRun(appId, run);
    current.setUserId(text(run, "principal"));
    try {
      output = AppFlowRecordContext.as(rootRunId, () -> historyContext.as(new Snapshot(rootRunId, taskId, text(run, "initiator")), () -> messages.businessEffect(appId, records.schema(appId, text(object(cfg.get("form")), "name")), taskId, () -> records.execute(appId, cfg, variables))));
    } finally {
      current.setUserId(actor);
    }
    var outputs = new LinkedHashMap<>(object(variables.get("nodes")));
    outputs.put(nodeKey, output);
    variables.put("nodes", outputs);
    if (!text(cfg, "output").isBlank()) variables.put(text(cfg, "output"), output);
    log(appId, taskId, runId, node, input, output, variables);
    complete(appId, run, taskId, variables);
  }

  public String rootRun(String appId, Map<String, Object> run) {
    return state.rootId(appId, run);
  }

  public void log(String appId, String id, String runId, Map<String, Object> node, Map<String, Object> input, Object output, Map<String, Object> variables) {
    var nodes = new LinkedHashMap<>(object(variables.get("nodes")));
    nodes.put(text(node, "id"), output);
    variables.put("nodes", nodes);
    state.log(appId, state.run(appId, runId), id, node, input, output, variables);
  }

  private void finish(String appId, Map<String, Object> run, String status, Map<String, Object> variables) {
    String id = text(run, "id");
    appFlowInstanceRepository.update(appId, id, Map.of("status", status, "updated_at", LocalDateTime.now()));
    for (var task : appApprovalTaskRepository.findByRunId(appId, id))
      if (Set.of("PENDING", "WAITING").contains(text(task, "status")))
        appApprovalTaskRepository.update(appId, text(task, "id"), Map.of("status", "CANCELLED"));
    if (text(run, "parent_task_id").isEmpty()) {
      String flowName = text(appFlowDeploymentRepository.get(appId, text(run, "flow_deploy_id")), "flow_name");
      if (!text(run, "form_name").isBlank())
        history.flowFinished(appId, text(run, "form_name"), text(run, "record_id"), status, flowName,
          text(run, "initiator"), id);
      if (!text(run, "initiator").isBlank())
        notificationService.result(appId, id, text(run, "initiator"), flowName, status);
      for (var lock : appFlowRecordLockRepository.findByRunId(appId, id))
        appFlowRecordLockRepository.delete(appId, text(lock, "id"));
      return;
    }
    var parent = state.parent(appId, run);
    if (!status.equals("SUCCEEDED")) {
      terminate(appId, parent, status);
      return;
    }
    Map<String, Object> parentNode = node(appId, parent, text(state.nodeInstance(appId, text(run, "parent_task_id")), "node_key"));
    Map<String, Object> parentVariables = new LinkedHashMap<>(state.variables(appId, parent));
    for (var mapping : objects(config(parentNode).get("outputs")))
      parentVariables.put(text(mapping, "field"), value(object(mapping.get("value")), variables));
    log(appId, text(run, "parent_task_id"), text(parent, "id"), parentNode, object(run.get("snapshot")), variables, parentVariables);
    complete(appId, parent, text(run, "parent_task_id"), parentVariables);
  }

  public void terminate(String appId, Map<String, Object> run, String reason) {
    if (text(run, "flow_instance_id").isEmpty()) {
      var starts = runtimeMessageRepository.findByMessageId(appId, "start:" + text(run, "id"));
      if (!starts.isEmpty() && !text(starts.getFirst(), "state").equals("DONE")) {
        runtimeMessageRepository.update(appId, text(starts.getFirst(), "id"), Map.of("state", "CANCELLED"));
        finish(appId, run, reason.equals("REJECTED") ? "REJECTED" : "TERMINATED", state.variables(appId, run));
        return;
      }
      for (var message : runtimeMessageRepository.findByType(appId, "flow.event")) {
        var payload = object(message.get("payload"));
        if (text(payload, "routingKey").endsWith("instance.started") && text(object(payload.get("variables")), "__runId").equals(text(run, "id"))) {
          run.put("flow_instance_id", text(payload, "flowInstanceId"));
          break;
        }
      }
    }
    appFlowInstanceRepository.update(appId, text(run, "id"), Map.of("status", reason.equals("REJECTED") ? "REJECTING" : "TERMINATING", "updated_at", LocalDateTime.now()));
    for (var child : state.children(appId, run))
      if (!Set.of("SUCCEEDED", "REJECTED", "TERMINATED").contains(text(child, "status")))
        terminate(appId, child, reason);
    messages.enqueue(appId, "terminate:" + text(run, "id"), "flow.command", Map.of("action", "terminate", "handler", "app-flow", "instanceId", text(run, "flow_instance_id"), "orderingKey", state.rootId(appId, run)));
  }
}
