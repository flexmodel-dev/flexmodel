package dev.flexmodel.apps.approval;

import dev.flexmodel.common.messaging.RuntimeMessageRepository;
import dev.flexmodel.directory.DirectoryService;
import dev.flexmodel.common.*;
import dev.flexmodel.common.messaging.ReliableMessages;
import dev.flexmodel.flow.event.DurableFlowListener;
import dev.flexmodel.notification.NotificationService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ClientErrorException;

import java.time.LocalDateTime;
import java.util.*;

import static dev.flexmodel.apps.AppRecordHistoryContext.*;
import static dev.flexmodel.apps.approval.AppApprovalJson.*;
import static dev.flexmodel.common.Values.*;

/**
 * Form integration reacts to Flow events; Flow never calls the form or approval services.
 */
@ApplicationScoped
public class AppApprovalRuntime implements DurableFlowListener {

  @Inject
  NotificationService notificationService;
  @Inject
  AppApprovalInstanceRepository appApprovalInstanceRepository;
  @Inject
  AppApprovalTaskRepository appApprovalTaskRepository;
  @Inject
  AppApprovalRecordLockRepository appApprovalRecordLockRepository;
  @Inject
  AppApprovalFlowDefinitionRepository appApprovalFlowDefinitionRepository;
  @Inject
  AppApprovalFlowDeploymentRepository appApprovalFlowDeploymentRepository;
  @Inject
  RuntimeMessageRepository runtimeMessageRepository;
  @Inject
  AppApprovalFlowState state;
  @Inject
  ProjectUnitOfWork transactions;
  @Inject
  AppApprovalFlowService appApprovalFlowService;
  @Inject
  AppApprovalRecordActions records;
  @Inject
  DirectoryService directoryService;
  @Inject
  ReliableMessages messages;
  @Inject
  SessionContext current;
  @Inject
  dev.flexmodel.apps.AppRecordHistoryRecorder history;
  @Inject
  dev.flexmodel.apps.AppRecordHistoryContext historyContext;

  public Map<String, Object> submit(String appId, String form, String recordId, String flowId, String key) {
    if (key == null || key.isBlank() || key.length() > 120) throw new ValidationException("提交必须携带有效幂等键");
    String actor = current.getUserId();
    String requestKey = actor + ":" + key;
    if (!current.isAuthenticatedUser()) throw new jakarta.ws.rs.ForbiddenException("请使用个人账号登录后提交审批");
    if (objects(directoryService.directory().get("members")).stream().noneMatch(member -> text(member, "id").equals(actor)))
      throw new ValidationException("仅有效登录用户可以提交审批");
    try {
      return transactions.execute(appId, () -> {
        ProjectUnitOfWork.lock(appId, form, "id", recordId);
        var existing = appApprovalInstanceRepository.findByRequestKey(appId, requestKey);
        if (!existing.isEmpty()) {
          return reuse(appId, existing.getFirst(), form, recordId, flowId);
        }
        Map<String, Object> flow = appApprovalFlowDefinitionRepository.get(appId, flowId);
        Map<String, Object> document = appApprovalFlowService.get(appId, flowId);
        if (!text(document, "status").equals("enabled") || !text(object(document.get("form")), "name").equals(form))
          throw new ValidationException("请选择关联当前表单的已启用流程");
        String schema = records.schema(appId, form);
        var locks = appApprovalRecordLockRepository.findByRecord(appId, schema, form, recordId);
        if (!locks.isEmpty()) throw new ClientErrorException("该记录已在审批中", 409);
        Map<String, Object> snapshot = records.snapshot(appId, form, recordId);
        String id = UUID.randomUUID().toString();
        Map<String, Object> variables = new LinkedHashMap<>(snapshot);
        variables.putAll(Map.of("record", snapshot, "initiator", actor, "nodeMembers", Map.of(), "__runId", id, "__rootRunId", id));
        checkSize(variables);
        Map<String, Object> run = run(id, requestKey, flowId, text(AppApprovalFlowModel.metadata(flow), "deploymentId"), form, recordId, actor, snapshot);
        Map<String, Object> result = transactions.executePlatform(appId, () -> {
          appApprovalInstanceRepository.insert(appId, run);
          appApprovalRecordLockRepository.insert(appId, Map.of("id", id, "run_id", id, "schema_name", schema, "model_name", form, "record_id", recordId));
          start(appId, run, variables);
          return state.run(appId, id);
        });
        String flowName = text(appApprovalFlowDeploymentRepository.get(appId, text(run, "flow_deploy_id")), "flow_name");
        ProjectUnitOfWork.afterCommit(() ->
          history.approvalSubmitted(appId, form, recordId, flowName, actor, id));
        return result;
      });
    } catch (RuntimeException error) {
      // Different records can race for one request key; read the winner after rollback, without replaying work.
      if (!SqlErrors.constraintViolation(error)) throw error;
      var existing = appApprovalInstanceRepository.findByRequestKey(appId, requestKey);
      if (existing.isEmpty()) throw error;
      return reuse(appId, existing.getFirst(), form, recordId, flowId);
    }
  }

  private Map<String, Object> reuse(String appId, Map<String, Object> run, String form, String recordId, String flowId) {
    if (!text(run, "form_name").equals(form) || !text(run, "record_id").equals(recordId) || !text(run, "flow_id").equals(flowId))
      throw new ClientErrorException("幂等键已用于其他审批请求", 409);
    return state.run(appId, text(run, "id"));
  }

  private Map<String, Object> run(String id, String key, String flowId, String deploymentId, String form, String recordId, String actor, Map<String, Object> snapshot) {
    return new LinkedHashMap<>(Map.ofEntries(Map.entry("id", id), Map.entry("request_key", key), Map.entry("flow_id", flowId), Map.entry("flow_deploy_id", deploymentId), Map.entry("form_name", form),
      Map.entry("record_id", recordId), Map.entry("initiator", actor), Map.entry("status", "ACTIVE"), Map.entry("snapshot", snapshot),
      Map.entry("created_at", LocalDateTime.now()), Map.entry("updated_at", LocalDateTime.now())));
  }

  private void start(String appId, Map<String, Object> run, Map<String, Object> variables) {
    messages.enqueue(appId, "start:" + text(run, "id"), "flow.command", Map.of("action", "start", "handler", "approval-flow", "deploymentId", text(run, "flow_deploy_id"), "initiator", text(run, "initiator"), "variables", variables));
  }

  public void complete(String appId, Map<String, Object> run, String taskId, Map<String, Object> variables) {
    checkSize(variables);
    messages.enqueue(appId, "complete:" + taskId, "flow.command", Map.of("action", "complete", "handler", "approval-flow", "instanceId", text(run, "flow_instance_id"), "taskId", taskId, "variables", variables, "orderingKey", state.rootId(appId, run)));
  }

  private void checkSize(Map<String, Object> variables) {
    if (dev.flexmodel.JsonUtils.toJsonString(variables).length() > 900000)
      throw new ValidationException("流程变量超过 900KB，请减少返回字段或记录数量");
  }

  public Map<String, Object> node(String appId, Map<String, Object> run, String nodeId) {
    var flow = appApprovalFlowDeploymentRepository.deployedFlow(appId, text(run, "flow_deploy_id"));
    return flatten(objects(object(flow.get("definition")).get("nodes"))).stream()
      .filter(node -> text(node, "id").equals(nodeId)).findFirst().orElseThrow(() -> new ValidationException("发布版本中不存在该节点"));
  }

  public Map<String, Object> eventRun(String appId, Map<String, Object> event) {
    String runId = text(object(event.get("variables")), "__runId");
    if (!runId.isEmpty()) {
      var run = appApprovalInstanceRepository.find(appId, runId);
      if (run == null) return null;
      run = state.lock(appId, runId, state.rootId(appId, run));
      if (!text(run, "flow_deploy_id").equals(text(event, "flowDeployId"))) return null;
      if (!text(run, "flow_instance_id").isEmpty() && !text(run, "flow_instance_id").equals(text(event, "flowInstanceId")))
        return null;
      return run;
    }
    var candidates = appApprovalInstanceRepository.findByFlowInstanceId(appId, text(event, "flowInstanceId"));
    return candidates.isEmpty() ? null : state.lock(appId, text(candidates.getFirst(), "id"), state.rootId(appId, candidates.getFirst()));
  }

  @Override
  public void onEvent(String appId, String eventId, String routingKey, Map<String, Object> event) {
    Map<String, Object> run = eventRun(appId, event);
    if (run == null) return;
    String runId = text(run, "id"), instanceId = text(event, "flowInstanceId");
    if (routingKey.endsWith("instance.started")) {
      String status = Set.of("SUBMITTING", "RUNNING").contains(text(run, "status")) ? "ACTIVE" : text(run, "status");
      appApprovalInstanceRepository.update(appId, runId, Map.of("flow_instance_id", instanceId, "status", status, "updated_at", LocalDateTime.now()));
      return;
    }
    if (text(run, "flow_instance_id").isEmpty()) throw new IllegalStateException("等待启动事件确认后再处理后续事件");
    if (Set.of("APPROVED", "REJECTED", "TERMINATED").contains(text(run, "status"))) return;
    Map<String, Object> variables = new LinkedHashMap<>(object(event.get("variables")));
    if (routingKey.endsWith("instance.completed")) {
      finish(appId, run, "APPROVED", variables);
      return;
    }
    if (routingKey.endsWith("instance.terminated")) {
      finish(appId, run, "REJECTING".equals(text(run, "status")) ? "REJECTED" : "TERMINATED", state.variables(appId, run));
      return;
    }
    if (routingKey.endsWith("instance.failed")) {
      appApprovalInstanceRepository.update(appId, runId, Map.of("status", "ERROR", "error", text(event, "errorMessage")));
      return;
    }
    if (!routingKey.equals(dev.flexmodel.flow.event.FlowEventTypes.USER_TASK_SUSPENDED)) return;
    Map<String, Object> attributes = object(event.get("nodeAttributes"));
    if (!"approval-flow".equals(text(attributes, "handler"))) return;
    String taskId = text(event, "nodeInstanceId"), nodeKey = text(event, "nodeKey");
    if (state.completed(appId, taskId)) return;
    if (!Set.of("RUNNING", "SUBMITTING").contains(text(run, "status"))) return;
    Map<String, Object> node = node(appId, run, nodeKey), cfg = config(node);
    String kind = text(cfg, "kind");
    if (kind.equals("subflow")) {
      String childId = UUID.nameUUIDFromBytes((appId + ":" + taskId).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
      if (appApprovalInstanceRepository.find(appId, childId) != null) return;
      var deployment = appApprovalFlowDeploymentRepository.get(appId, text(run, "flow_deploy_id"));
      String childDeploymentId = text(object(AppApprovalFlowModel.metadata(deployment).get("children")), nodeKey);
      Map<String, Object> childVariables = new LinkedHashMap<>(variables);
      for (var input : objects(cfg.get("inputs")))
        childVariables.put(text(input, "field"), value(object(input.get("value")), variables));
      childVariables.put("__runId", childId);
      var child = run(childId, "child:" + taskId, text(cfg, "flowId"), childDeploymentId, text(run, "form_name"), text(run, "record_id"), text(run, "initiator"), object(run.get("snapshot")));
      child.put("parent_task_id", taskId);
      appApprovalInstanceRepository.insert(appId, child);
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
    current.setUserId(text(run, "initiator"));
    try {
      output = AppApprovalRecordContext.as(rootRunId, () -> historyContext.as(new Snapshot(rootRunId, taskId, text(run, "initiator")), () -> records.execute(appId, cfg, variables)));
    } finally {
      current.setUserId(actor);
    }
    if (!text(cfg, "output").isBlank()) variables.put(text(cfg, "output"), output);
    log(appId, taskId, runId, node, input, output, variables);
    complete(appId, run, taskId, variables);
  }

  public String rootRun(String appId, Map<String, Object> run) {
    return state.rootId(appId, run);
  }

  public void log(String appId, String id, String runId, Map<String, Object> node, Map<String, Object> input, Object output, Map<String, Object> variables) {
    state.log(appId, state.run(appId, runId), id, node, input, output, variables);
  }

  private void finish(String appId, Map<String, Object> run, String status, Map<String, Object> variables) {
    String id = text(run, "id");
    appApprovalInstanceRepository.update(appId, id, Map.of("status", status, "updated_at", LocalDateTime.now()));
    for (var task : appApprovalTaskRepository.findByRunId(appId, id))
      if (Set.of("PENDING", "WAITING").contains(text(task, "status")))
        appApprovalTaskRepository.update(appId, text(task, "id"), Map.of("status", "CANCELLED"));
    if (text(run, "parent_task_id").isEmpty()) {
      String flowName = text(appApprovalFlowDeploymentRepository.get(appId, text(run, "flow_deploy_id")), "flow_name");
      history.approvalFinished(appId, text(run, "form_name"), text(run, "record_id"), status, flowName,
        text(run, "initiator"), id);
      notificationService.result(appId, id, text(run, "initiator"), flowName, status);
      for (var lock : appApprovalRecordLockRepository.findByRunId(appId, id))
        appApprovalRecordLockRepository.delete(appId, text(lock, "id"));
      return;
    }
    var parent = state.parent(appId, run);
    if (!status.equals("APPROVED")) {
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
    appApprovalInstanceRepository.update(appId, text(run, "id"), Map.of("status", reason.equals("REJECTED") ? "REJECTING" : "TERMINATING", "updated_at", LocalDateTime.now()));
    for (var child : state.children(appId, run))
      if (!Set.of("APPROVED", "REJECTED", "TERMINATED").contains(text(child, "status")))
        terminate(appId, child, reason);
    messages.enqueue(appId, "terminate:" + text(run, "id"), "flow.command", Map.of("action", "terminate", "handler", "approval-flow", "instanceId", text(run, "flow_instance_id"), "orderingKey", state.rootId(appId, run)));
  }
}
