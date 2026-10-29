package dev.flexmodel.apps.approval;

import dev.flexmodel.apps.AppRecordHistoryRecorder;
import dev.flexmodel.common.ProjectUnitOfWork;
import dev.flexmodel.common.SessionContext;
import dev.flexmodel.common.ValidationException;
import dev.flexmodel.common.authz.PermissionHelper;
import dev.flexmodel.common.messaging.RuntimeMessageRepository;
import dev.flexmodel.directory.DirectoryService;
import dev.flexmodel.flow.event.DurableFlowListener;
import dev.flexmodel.flow.event.FlowEventTypes;
import dev.flexmodel.project.ProjectRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.ForbiddenException;

import java.time.LocalDateTime;
import java.util.*;

import static dev.flexmodel.apps.approval.AppApprovalJson.config;
import static dev.flexmodel.common.Values.*;

/**
 * Approval owns assignment and signatures. It consumes suspended tasks and emits completion commands.
 */
@ApplicationScoped
public class AppApprovalService implements DurableFlowListener {
  @Inject
  dev.flexmodel.notification.NotificationService notificationService;
  @Inject
  AppApprovalInstanceRepository appApprovalInstanceRepository;
  @Inject
  AppApprovalTaskRepository appApprovalTaskRepository;
  @Inject
  AppApprovalWaitRepository appApprovalWaitRepository;
  @Inject
  AppApprovalFlowDeploymentRepository appApprovalFlowDeploymentRepository;
  @Inject
  RuntimeMessageRepository runtimeMessageRepository;
  @Inject
  AppApprovalFlowState state;
  @Inject
  AppApprovalRuntime runtime;
  @Inject
  DirectoryService directoryService;
  @Inject
  ProjectUnitOfWork transactions;
  @Inject
  SessionContext current;
  @Inject
  ProjectRepository projectRepository;
  @Inject
  AppRecordHistoryRecorder history;

  @Override
  public void onEvent(String appId, String eventId, String routingKey, Map<String, Object> event) {
    if (!routingKey.equals(FlowEventTypes.USER_TASK_SUSPENDED) || !text(object(event.get("nodeAttributes")), "handler").equals("approval"))
      return;
    Map<String, Object> run = runtime.eventRun(appId, event);
    if (run == null || !Set.of("RUNNING", "SUBMITTING").contains(text(run, "status"))) return;
    String taskId = text(event, "nodeInstanceId");
    if (appApprovalWaitRepository.find(appId, taskId) != null) return;
    Map<String, Object> node = runtime.node(appId, run, text(event, "nodeKey"));
    Map<String, Object> cfg = config(node), variables = object(event.get("variables"));
    List<String> members = directoryService.resolve(objects(cfg.get("rules")), text(run, "initiator"), variables);
    if (members.isEmpty())
      throw new ValidationException("审批节点未解析出有效执行人：" + text(object(node.get("data")), "title"));
    String mode = text(cfg, "signMode");
    appApprovalWaitRepository.insert(appId, Map.of("id", taskId, "run_id", text(run, "id"), "members", members, "status", "WAITING", "created_at", LocalDateTime.now()));
    for (int i = 0; i < members.size(); i++) {
      appApprovalTaskRepository.insert(appId, Map.of("id", taskId + ":" + i, "wait_id", taskId, "run_id", text(run, "id"), "user_id", members.get(i),
        "position", i, "status", mode.equals("sequential") && i > 0 ? "WAITING" : "PENDING", "created_at", LocalDateTime.now()));
      if (!mode.equals("sequential") || i == 0)
        notificationService.pending(appId, text(run, "id"), taskId + ":" + i, members.get(i), text(object(node.get("data")), "title"));
    }
  }

  public Map<String, Object> decide(String appId, String id, String decision, String comment, String key) {
    if (!Set.of("approve", "reject").contains(decision) || key == null || key.isBlank() || key.length() > 120 || comment.length() > 2000)
      throw new ValidationException("签核参数无效");
    String actor = current.getUserId();
    if (!current.isAuthenticatedUser()) throw new ForbiddenException("请使用个人账号登录后签核");
    if (objects(directoryService.directory().get("members")).stream().noneMatch(member -> text(member, "id").equals(actor)))
      throw new ForbiddenException("需要有效用户登录后签核");
    var initialTask = appApprovalTaskRepository.get(appId, id);
    String root = state.rootId(appId, state.run(appId, text(initialTask, "run_id")));
    return transactions.executePlatform(appId, () -> {
      state.lock(appId, text(initialTask, "run_id"), root);
      var task = appApprovalTaskRepository.get(appId, id);
      if (!actor.equals(text(task, "user_id"))) throw new ForbiddenException("只能处理自己的待办");
      if (key.equals(text(task, "request_key"))) {
        if (!decision.equals(text(task, "decision")) || !comment.equals(text(task, "comment")))
          throw new ClientErrorException("幂等键对应的签核内容不一致", 409);
        return task;
      }
      if (!text(task, "status").equals("PENDING")) throw new ClientErrorException("该任务已处理或尚未轮到你审批", 409);
      var wait = state.waitView(appId, appApprovalWaitRepository.get(appId, text(task, "wait_id")));
      var run = state.run(appId, text(task, "run_id"));
      if (!text(wait, "status").equals("WAITING") || !text(run, "status").equals("RUNNING"))
        throw new ClientErrorException("审批已结束或暂停", 409);
      appApprovalTaskRepository.update(appId, id, Map.of("status", "DONE", "decision", decision, "comment", comment, "request_key", key, "decided_at", LocalDateTime.now()));
      String historyFlowName = text(appApprovalFlowDeploymentRepository.get(appId, text(run, "flow_deploy_id")), "flow_name");
      ProjectUnitOfWork.afterCommit(() -> history.taskDecided(appId, text(run, "form_name"), text(run, "record_id"),
        decision, comment, actor, text(wait, "title"), historyFlowName, text(run, "id"), id));
      var waitTasks = appApprovalTaskRepository.findByWaitId(appId, text(wait, "id")).stream()
        .sorted(Comparator.comparingInt(item -> ((Number) item.get("position")).intValue())).toList();
      String outcome = SignPolicy.outcome(text(wait, "sign_mode"), waitTasks.stream().map(item -> text(item, "decision")).toList());
      if (outcome.equals("WAITING")) {
        if (text(wait, "sign_mode").equals("sequential"))
          waitTasks.stream().filter(item -> text(item, "status").equals("WAITING")).findFirst()
            .ifPresent(next -> {
              appApprovalTaskRepository.update(appId, text(next, "id"), Map.of("status", "PENDING"));
              notificationService.pending(appId, text(run, "id"), text(next, "id"), text(next, "user_id"), text(wait, "title"));
            });
      } else {
        appApprovalWaitRepository.update(appId, text(wait, "id"), Map.of("status", outcome));
        for (var peer : waitTasks)
          if (Set.of("PENDING", "WAITING").contains(text(peer, "status")))
            appApprovalTaskRepository.update(appId, text(peer, "id"), Map.of("status", "CANCELLED"));
        if (outcome.equals("REJECTED")) runtime.terminate(appId, run, "REJECTED");
        else {
          Map<String, Object> variables = new LinkedHashMap<>(state.variables(appId, run));
          Map<String, Object> nodeMembers = new LinkedHashMap<>(object(variables.get("nodeMembers")));
          nodeMembers.put(text(wait, "node_key"), waitTasks.stream().filter(item -> text(item, "decision").equals("approve")).map(item -> text(item, "user_id")).toList());
          variables.put("nodeMembers", nodeMembers);
          runtime.log(appId, text(wait, "id"), text(run, "id"), runtime.node(appId, run, text(wait, "node_key")), object(run.get("snapshot")), Map.of("members", wait.get("members"), "decisions", waitTasks), variables);
          runtime.complete(appId, run, text(wait, "id"), variables);
        }
      }
      return appApprovalTaskRepository.get(appId, id);
    });
  }

  public List<Map<String, Object>> tasks(String appId, String view, String search) {
    if (!current.isAuthenticatedUser()) throw new ForbiddenException("请使用个人账号查看审批任务");
    String actor = current.getUserId();
    List<Map<String, Object>> result = new ArrayList<>();
    for (var project : projectRepository.findTopLevelProjects()) {
      if (appId != null && !appId.isBlank() && !appId.equals(project.getId())) continue;
      if ("default".equals(project.getId())) continue;
      for (var task : appApprovalTaskRepository.findByUser(project.getId(), actor)) {
        if (!(view.equals("handled") ? text(task, "status").equals("DONE") : text(task, "status").equals("PENDING")))
          continue;
        var run = state.run(project.getId(), text(task, "run_id"));
        var wait = state.waitView(project.getId(), appApprovalWaitRepository.get(project.getId(), text(task, "wait_id")));
        Map<String, Object> item = summary(project.getId(), project.getName(), run);
        item.putAll(Map.of("taskId", text(task, "id"), "nodeTitle", text(wait, "title"), "taskStatus", text(task, "status"), "decision", text(task, "decision")));
        if (text(item, "title").contains(search) || text(item, "nodeTitle").contains(search)) result.add(item);
      }
    }
    return sorted(result);
  }

  public List<Map<String, Object>> initiated(String appId, String search) {
    if (!current.isAuthenticatedUser()) throw new ForbiddenException("请使用个人账号查看审批实例");
    List<Map<String, Object>> result = new ArrayList<>();
    for (var project : projectRepository.findTopLevelProjects()) {
      if ("default".equals(project.getId()) || appId != null && !appId.isBlank() && !appId.equals(project.getId()))
        continue;
      for (var run : appApprovalInstanceRepository.findByInitiator(project.getId(), current.getUserId()))
        if (text(run, "parent_task_id").isEmpty()) {
          var item = summary(project.getId(), project.getName(), run);
          if (text(item, "title").contains(search)) result.add(item);
        }
    }
    return sorted(result);
  }

  private List<Map<String, Object>> sorted(List<Map<String, Object>> values) {
    return values.stream().sorted(Comparator.comparing((Map<String, Object> item) -> text(item, "createdAt")).reversed()).toList();
  }

  public Map<String, Object> summary(String appId, String appName, Map<String, Object> run) {
    run = state.run(appId, text(run, "id"));
    var deployment = appApprovalFlowDeploymentRepository.get(appId, text(run, "flow_deploy_id"));
    return new LinkedHashMap<>(Map.ofEntries(Map.entry("id", text(run, "id")), Map.entry("appId", appId), Map.entry("appName", appName), Map.entry("title", text(deployment, "flow_name")),
      Map.entry("formName", text(run, "form_name")), Map.entry("recordId", text(run, "record_id")), Map.entry("initiator", text(run, "initiator")), Map.entry("status", text(run, "status")), Map.entry("createdAt", run.get("created_at"))));
  }

  public List<Map<String, Object>> recordHistory(String appId, String form, String recordId) {
    return appApprovalInstanceRepository.findByFormRecord(appId, form, recordId).stream()
      .filter(run -> text(run, "parent_task_id").isEmpty())
      .map(run -> summary(appId, "", run)).toList();
  }

  public Map<String, Object> detail(String appId, String id) {
    var run = state.run(appId, id);
    boolean involved = current.getUserId() != null && (current.getUserId().equals(text(run, "initiator"))
      || !appApprovalTaskRepository.findByRunAndUser(appId, id, current.getUserId()).isEmpty());
    var parent = run;
    while (!involved && !text(parent, "parent_task_id").isBlank()) {
      parent = state.parent(appId, parent);
      involved = Objects.equals(current.getUserId(), text(parent, "initiator"))
        || !appApprovalTaskRepository.findByRunAndUser(appId, text(parent, "id"), current.getUserId()).isEmpty();
    }
    if (!current.isAuthenticatedUser() || !involved && !admin()) throw new ForbiddenException("无权查看该审批");
    var waits = appApprovalWaitRepository.findByRunId(appId, id).stream().map(wait -> state.waitView(appId, wait)).toList();
    Map<String, Object> result = summary(appId, "", run);
    result.putAll(Map.of("snapshot", run.get("snapshot"), "variables", state.variables(appId, run), "error", text(run, "error"),
      "tasks", appApprovalTaskRepository.findByRunId(appId, id), "waits", waits, "upcoming", state.upcoming(appId, run, waits),
      "logs", state.logs(appId, run), "children", state.children(appId, run).stream().map(child -> summary(appId, "", child)).toList()));
    result.put("ending", state.ending(appId, run));
    List<Map<String, Object>> failures = runtimeMessageRepository.findUndone(appId).stream().filter(message -> {
      var payload = object(message.get("payload"));
      return text(payload, "instanceId").equals(text(run, "flow_instance_id")) && !text(run, "flow_instance_id").isEmpty()
        || text(payload, "flowInstanceId").equals(text(run, "flow_instance_id")) && !text(run, "flow_instance_id").isEmpty()
        || text(object(payload.get("variables")), "__runId").equals(id);
    }).map(message -> Map.of("messageId", message.get("message_id"), "state", message.get("state"), "attempts", message.get("attempts"), "error", text(message, "error"))).toList();
    result.put("delivery", failures);
    result.put("notificationDelivery", notificationDelivery(appId, id));
    result.put("canManage", admin());
    return result;
  }

  public boolean admin() {
    if (!current.isAuthenticatedUser()) return false;
    if (current.getPermissions() != null)
      return PermissionHelper.hasPermission(current.getPermissions(), "flow:deploy");
    return objects(directoryService.directory().get("members")).stream().anyMatch(member -> text(member, "id").equals(current.getUserId()) && strings(member.get("roleIds")).contains("admin"));
  }

  private List<Map<String, Object>> notificationDelivery(String app, String id) {
    return runtimeMessageRepository.findByTypeUndone(app, "notification.deliver").stream()
      .filter(message -> text(object(message.get("payload")), "instance_id").equals(id))
      .map(message -> Map.of("messageId", message.get("message_id"), "state", message.get("state"), "attempts", message.get("attempts"), "error", text(message, "error"))).toList();
  }

  public void retryNotifications(String app, String id) {
    if (!admin()) throw new ForbiddenException("需要流程管理权限");
    String root = state.rootId(app, state.run(app, id));
    transactions.executePlatform(app, () -> {
      state.lock(app, id, root);
      for (var failure : notificationDelivery(app, id))
        if (text(failure, "state").equals("FAILED")) {
          for (var message : runtimeMessageRepository.findByMessageId(app, String.valueOf(failure.get("messageId"))))
            runtimeMessageRepository.update(app, text(message, "id"), Map.of("state", "PENDING", "attempts", 0, "error", "", "available_at", LocalDateTime.now()));
        }
      return null;
    });
  }

  public void manage(String appId, String id, String action) {
    if (!admin()) throw new ForbiddenException("需要流程管理权限");
    String root = state.rootId(appId, state.run(appId, id));
    transactions.executePlatform(appId, () -> {
      var run = state.lock(appId, id, root);
      if (Set.of("APPROVED", "REJECTED", "TERMINATED").contains(text(run, "status")))
        throw new ClientErrorException("审批已结束", 409);
      if (action.equals("terminate")) runtime.terminate(appId, run, "TERMINATED");
      else if (action.equals("retry")) {
        var detail = detail(appId, id);
        for (var failure : objects(detail.get("delivery")))
          if (text(failure, "state").equals("FAILED")) {
            for (var message : runtimeMessageRepository.findByMessageId(appId, String.valueOf(failure.get("messageId"))))
              runtimeMessageRepository.update(appId, text(message, "id"), Map.of("state", "PENDING", "attempts", 0, "available_at", LocalDateTime.now()));
          }
      } else throw new ValidationException("操作无效");
      return null;
    });
  }
}
