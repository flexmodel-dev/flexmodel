package dev.flexmodel.approval;

import dev.flexmodel.common.*;
import dev.flexmodel.directory.DirectoryService;
import dev.flexmodel.common.authz.PermissionHelper;
import dev.flexmodel.flow.event.*;
import dev.flexmodel.project.ProjectRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.ForbiddenException;

import java.time.LocalDateTime;
import java.util.*;

import static dev.flexmodel.approval.ApprovalJson.*;
import static dev.flexmodel.common.Values.*;
import static dev.flexmodel.query.Expressions.field;

/**
 * Approval owns assignment and signatures. It consumes suspended tasks and emits completion commands.
 */
@ApplicationScoped
public class ApprovalService implements DurableFlowListener {
  @Inject
  dev.flexmodel.notification.NotificationService notifications;
  @Inject
  ApprovalRepository repository;
  @Inject
  ApprovalFlowState state;
  @Inject
  ApprovalRuntime runtime;
  @Inject
  DirectoryService directory;
  @Inject
  ProjectUnitOfWork transactions;
  @Inject
  SessionContext current;
  @Inject
  ProjectRepository projects;

  @Override
  public void onEvent(String appId, String eventId, String routingKey, Map<String, Object> event) {
    if (!routingKey.equals(FlowEventTypes.USER_TASK_SUSPENDED) || !text(object(event.get("nodeAttributes")), "handler").equals("approval"))
      return;
    Map<String, Object> run = runtime.eventRun(appId, event);
    if (run == null || !Set.of("RUNNING", "SUBMITTING").contains(text(run, "status"))) return;
    String taskId = text(event, "nodeInstanceId");
    if (repository.optional(appId, "f_approval_wait", taskId) != null) return;
    Map<String, Object> node = runtime.node(appId, run, text(event, "nodeKey"));
    Map<String, Object> cfg = config(node), variables = object(event.get("variables"));
    List<String> members = directory.resolve(objects(cfg.get("rules")), text(run, "initiator"), variables);
    if (members.isEmpty())
      throw new ValidationException("审批节点未解析出有效执行人：" + text(object(node.get("data")), "title"));
    String mode = text(cfg, "signMode");
    repository.insert(appId, "f_approval_wait", Map.of("id", taskId, "run_id", text(run, "id"), "members", members, "status", "WAITING", "created_at", LocalDateTime.now()));
    for (int i = 0; i < members.size(); i++) {
      repository.insert(appId, "f_approval_task", Map.of("id", taskId + ":" + i, "wait_id", taskId, "run_id", text(run, "id"), "user_id", members.get(i),
        "position", i, "status", mode.equals("sequential") && i > 0 ? "WAITING" : "PENDING", "created_at", LocalDateTime.now()));
      if (!mode.equals("sequential") || i == 0)
        notifications.pending(appId, text(run, "id"), taskId + ":" + i, members.get(i), text(object(node.get("data")), "title"));
    }
  }

  public Map<String, Object> decide(String appId, String id, String decision, String comment, String key) {
    if (!Set.of("approve", "reject").contains(decision) || key == null || key.isBlank() || key.length() > 120 || comment.length() > 2000)
      throw new ValidationException("签核参数无效");
    String actor = current.getUserId();
    if (!current.isAuthenticatedUser()) throw new ForbiddenException("请使用个人账号登录后签核");
    if (objects(directory.directory().get("members")).stream().noneMatch(member -> text(member, "id").equals(actor)))
      throw new ForbiddenException("需要有效用户登录后签核");
    var initialTask = repository.get(appId, "f_approval_task", id);
    String root = state.rootId(appId, state.run(appId, text(initialTask, "run_id")));
    return transactions.execute(appId, () -> {
      state.lock(appId, text(initialTask, "run_id"), root);
      var task = repository.get(appId, "f_approval_task", id);
      if (!actor.equals(text(task, "user_id"))) throw new ForbiddenException("只能处理自己的待办");
      if (key.equals(text(task, "request_key"))) {
        if (!decision.equals(text(task, "decision")) || !comment.equals(text(task, "comment")))
          throw new ClientErrorException("幂等键对应的签核内容不一致", 409);
        return task;
      }
      if (!text(task, "status").equals("PENDING")) throw new ClientErrorException("该任务已处理或尚未轮到你审批", 409);
      var wait = state.waitView(appId, repository.get(appId, "f_approval_wait", text(task, "wait_id")));
      var run = state.run(appId, text(task, "run_id"));
      if (!text(wait, "status").equals("WAITING") || !text(run, "status").equals("RUNNING"))
        throw new ClientErrorException("审批已结束或暂停", 409);
      repository.update(appId, "f_approval_task", id, Map.of("status", "DONE", "decision", decision, "comment", comment, "request_key", key, "decided_at", LocalDateTime.now()));
      var tasks = repository.list(appId, "f_approval_task", field("wait_id").eq(text(wait, "id"))).stream()
        .sorted(Comparator.comparingInt(item -> ((Number) item.get("position")).intValue())).toList();
      String outcome = SignPolicy.outcome(text(wait, "sign_mode"), tasks.stream().map(item -> text(item, "decision")).toList());
      if (outcome.equals("WAITING")) {
        if (text(wait, "sign_mode").equals("sequential"))
          tasks.stream().filter(item -> text(item, "status").equals("WAITING")).findFirst()
            .ifPresent(next -> {
              repository.update(appId, "f_approval_task", text(next, "id"), Map.of("status", "PENDING"));
              notifications.pending(appId, text(run, "id"), text(next, "id"), text(next, "user_id"), text(wait, "title"));
            });
      } else {
        repository.update(appId, "f_approval_wait", text(wait, "id"), Map.of("status", outcome));
        for (var peer : tasks)
          if (Set.of("PENDING", "WAITING").contains(text(peer, "status")))
            repository.update(appId, "f_approval_task", text(peer, "id"), Map.of("status", "CANCELLED"));
        if (outcome.equals("REJECTED")) runtime.terminate(appId, run, "REJECTED");
        else {
          Map<String, Object> variables = new LinkedHashMap<>(state.variables(appId, run));
          Map<String, Object> nodeMembers = new LinkedHashMap<>(object(variables.get("nodeMembers")));
          nodeMembers.put(text(wait, "node_key"), tasks.stream().filter(item -> text(item, "decision").equals("approve")).map(item -> text(item, "user_id")).toList());
          variables.put("nodeMembers", nodeMembers);
          runtime.log(appId, text(wait, "id"), text(run, "id"), runtime.node(appId, run, text(wait, "node_key")), object(run.get("snapshot")), Map.of("members", wait.get("members"), "decisions", tasks), variables);
          runtime.complete(appId, run, text(wait, "id"), variables);
        }
      }
      return repository.get(appId, "f_approval_task", id);
    });
  }

  public List<Map<String, Object>> tasks(String appId, String view, String search) {
    if (!current.isAuthenticatedUser()) throw new ForbiddenException("请使用个人账号查看审批任务");
    String actor = current.getUserId();
    List<Map<String, Object>> result = new ArrayList<>();
    for (var project : projects.findTopLevelProjects()) {
      if (appId != null && !appId.isBlank() && !appId.equals(project.getId())) continue;
      if ("default".equals(project.getId())) continue;
      for (var task : repository.list(project.getId(), "f_approval_task", field("user_id").eq(actor))) {
        if (!(view.equals("handled") ? text(task, "status").equals("DONE") : text(task, "status").equals("PENDING")))
          continue;
        var run = state.run(project.getId(), text(task, "run_id"));
        var wait = state.waitView(project.getId(), repository.get(project.getId(), "f_approval_wait", text(task, "wait_id")));
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
    for (var project : projects.findTopLevelProjects()) {
      if ("default".equals(project.getId()) || appId != null && !appId.isBlank() && !appId.equals(project.getId()))
        continue;
      for (var run : repository.list(project.getId(), "f_approval_instance", field("initiator").eq(current.getUserId())))
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
    var deployment = repository.deployment(appId, text(run, "flow_deploy_id"));
    return new LinkedHashMap<>(Map.ofEntries(Map.entry("id", text(run, "id")), Map.entry("appId", appId), Map.entry("appName", appName), Map.entry("title", text(deployment, "flow_name")),
      Map.entry("formName", text(run, "form_name")), Map.entry("recordId", text(run, "record_id")), Map.entry("initiator", text(run, "initiator")), Map.entry("status", text(run, "status")), Map.entry("createdAt", run.get("created_at"))));
  }

  public Map<String, Object> detail(String appId, String id) {
    var run = state.run(appId, id);
    boolean involved = current.getUserId() != null && (current.getUserId().equals(text(run, "initiator")) || !repository.list(appId, "f_approval_task", field("run_id").eq(id).and(field("user_id").eq(current.getUserId()))).isEmpty());
    var parent = run;
    while (!involved && !text(parent, "parent_task_id").isBlank()) {
      parent = state.parent(appId, parent);
      involved = Objects.equals(current.getUserId(), text(parent, "initiator")) || !repository.list(appId, "f_approval_task", field("run_id").eq(text(parent, "id")).and(field("user_id").eq(current.getUserId()))).isEmpty();
    }
    if (!current.isAuthenticatedUser() || !involved && !admin()) throw new ForbiddenException("无权查看该审批");
    Map<String, Object> result = summary(appId, "", run);
    result.putAll(Map.of("snapshot", run.get("snapshot"), "variables", state.variables(appId, run), "error", text(run, "error"),
      "tasks", repository.list(appId, "f_approval_task", field("run_id").eq(id)), "waits", repository.list(appId, "f_approval_wait", field("run_id").eq(id)).stream().map(wait -> state.waitView(appId, wait)).toList(),
      "logs", state.logs(appId, run), "children", state.children(appId, run).stream().map(child -> summary(appId, "", child)).toList()));
    List<Map<String, Object>> failures = repository.list(appId, "f_runtime_message", field("state").ne("DONE")).stream().filter(message -> {
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
    return objects(directory.directory().get("members")).stream().anyMatch(member -> text(member, "id").equals(current.getUserId()) && strings(member.get("roleIds")).contains("admin"));
  }

  private List<Map<String, Object>> notificationDelivery(String app, String id) {
    return repository.list(app, "f_runtime_message", field("type").eq("notification.deliver").and(field("state").ne("DONE"))).stream()
      .filter(message -> text(object(message.get("payload")), "instance_id").equals(id))
      .map(message -> Map.of("messageId", message.get("message_id"), "state", message.get("state"), "attempts", message.get("attempts"), "error", text(message, "error"))).toList();
  }

  public void retryNotifications(String app, String id) {
    if (!admin()) throw new ForbiddenException("需要流程管理权限");
    String root = state.rootId(app, state.run(app, id));
    transactions.execute(app, () -> {
      state.lock(app, id, root);
      for (var failure : notificationDelivery(app, id))
        if (text(failure, "state").equals("FAILED")) {
          for (var message : repository.list(app, "f_runtime_message", field("message_id").eq(failure.get("messageId"))))
            repository.update(app, "f_runtime_message", text(message, "id"), Map.of("state", "PENDING", "attempts", 0, "error", "", "available_at", LocalDateTime.now()));
        }
      return null;
    });
  }

  public void manage(String appId, String id, String action) {
    if (!admin()) throw new ForbiddenException("需要流程管理权限");
    String root = state.rootId(appId, state.run(appId, id));
    transactions.execute(appId, () -> {
      var run = state.lock(appId, id, root);
      if (Set.of("APPROVED", "REJECTED", "TERMINATED").contains(text(run, "status")))
        throw new ClientErrorException("审批已结束", 409);
      if (action.equals("terminate")) runtime.terminate(appId, run, "TERMINATED");
      else if (action.equals("retry")) {
        var detail = detail(appId, id);
        for (var failure : objects(detail.get("delivery")))
          if (text(failure, "state").equals("FAILED")) {
            for (var message : repository.list(appId, "f_runtime_message", field("message_id").eq(failure.get("messageId"))))
              repository.update(appId, "f_runtime_message", text(message, "id"), Map.of("state", "PENDING", "attempts", 0, "available_at", LocalDateTime.now()));
          }
      } else throw new ValidationException("操作无效");
      return null;
    });
  }
}
