package dev.flexmodel.apps.flow;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.apps.form.AppFormService;
import dev.flexmodel.apps.form.dto.AppFormResponse;
import dev.flexmodel.directory.DirectoryService;
import dev.flexmodel.flow.dto.model.FlowModel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.*;

import static dev.flexmodel.apps.flow.AppFlowJson.*;
import static dev.flexmodel.common.Values.*;

/**
 * Validate the editor view and add trusted execution attributes to a Flow deployment.
 */
@ApplicationScoped
public class AppFlowCompiler {
  @Inject
  AppFormService appFormService;
  @Inject
  DirectoryService directoryService;
  private static final Set<String> KINDS = Set.of("start", "end", "approval", "notification", "gateway", "branch", "create", "update", "query", "delete", "subflow");

  public List<Map<String, String>> validate(String appId, Map<String, Object> flow, List<Map<String, Object>> flows) {
    List<Map<String, String>> issues = new ArrayList<>();
    List<Map<String, Object>> nodes = objects(object(flow.get("definition")).get("nodes"));
    if (nodes.size() < 2 || !"start".equals(text(nodes.getFirst(), "type")) || !"end".equals(text(nodes.getLast(), "type")))
      issue(issues, "", "开始和结束节点必须保留在流程首尾");
    if (text(flow, "name").isBlank()) issue(issues, "", "请填写流程名称");
    var trigger = FlowTrigger.of(flow);
    try {
      FlowTrigger.validate(trigger);
    } catch (RuntimeException e) {
      issue(issues, nodes.isEmpty() ? "" : text(nodes.getFirst(), "id"), e.getMessage());
    }
    boolean scheduled = "SCHEDULE".equals(text(trigger, "type"));
    var form = scheduled ? null : requireForm(appId, Map.of("kind", "app", "name", text(trigger, "formName")), "", issues);
    if (form != null && "DATA_EVENT".equals(text(trigger, "type"))) {
      var fields = new HashSet<>(form.fields().stream().map(f -> f.name()).toList());
      if (!fields.containsAll(strings(trigger.get("watchedFields")))) issue(issues, "", "监听字段不存在");
      for (var condition : objects(object(trigger.get("conditions")).get("conditions"))) {
        String field = text(condition, "field");
        if (field.startsWith("record.")) field = field.substring(7);
        if (!fields.contains(field) || !ConditionOperator.valid(text(condition, "operator")))
          issue(issues, "", "触发条件字段或操作符无效");
        validateBinding(object(condition.get("value")), Set.of("record", "trigger"), "", issues);
      }
    }
    var allNodes = flatten(nodes);
    if (allNodes.stream().filter(node -> text(node, "type").equals("start")).count() != 1 || allNodes.stream().filter(node -> text(node, "type").equals("end")).count() != 1)
      issue(issues, "", "流程必须恰好包含一个开始和结束节点");
    if (nodes.stream().anyMatch(node -> text(node, "type").equals("branch")))
      issue(issues, "", "条件分支必须放在网关内");
    Set<String> ids = new HashSet<>();
    Set<String> inputs = new LinkedHashSet<>();
    inputs.addAll(Set.of("input", "trigger", "context", "nodes"));
    if (!scheduled) inputs.add("record");
    if (scheduled && allNodes.stream().anyMatch(n -> objects(config(n).get("rules")).stream().anyMatch(r -> "initiator".equals(text(r, "source")))))
      issue(issues, "", "定时流程不能使用发起人作为执行人来源");
    validatePath(appId, nodes, inputs, new LinkedHashSet<>(), ids, issues, flows);
    if (cyclic(text(flow, "id"), flow, flows, new HashSet<>(), new HashSet<>()))
      issue(issues, "", "子流程存在直接或间接循环引用");
    return issues;
  }

  private void validatePath(String appId, List<Map<String, Object>> nodes, Set<String> upstream, Set<String> upstreamApprovals, Set<String> ids, List<Map<String, String>> issues, List<Map<String, Object>> flows) {
    for (var node : nodes) {
      String id = text(node, "id"), kind = text(node, "type");
      Map<String, Object> cfg = config(node);
      if (!id.matches("[A-Za-z0-9_-]{1,100}") || !ids.add(id)) issue(issues, id, "节点标识无效或重复");
      if (!KINDS.contains(kind) || !kind.equals(text(cfg, "kind"))) issue(issues, id, "节点类型无效");
      if (!kind.equals("gateway") && !kind.equals("branch") && !objects(node.get("blocks")).isEmpty())
        issue(issues, id, "该节点不允许嵌套子节点");
      if (kind.equals("notification")) {
        for (String section : List.of("titleTemplate", "contentTemplate")) {
          String template = text(cfg, section);
          if (template.isBlank() || template.length() > (section.equals("titleTemplate") ? 200 : 5000))
            issue(issues, id, "请填写有效的通知标题和正文");
          try {
            for (String path : dev.flexmodel.notification.NotificationTemplates.paths(template))
              validateBinding(Map.of("kind", "variable", "value", path), upstream, id, issues);
          } catch (RuntimeException error) {
            issue(issues, id, "通知变量格式无效");
          }
        }
      }
      if (kind.equals("approval") || kind.equals("notification")) {
        if (kind.equals("approval") && !Set.of("all", "any", "sequential").contains(text(cfg, "signMode")))
          issue(issues, id, "签核类型无效");
        if (objects(cfg.get("rules")).isEmpty()) issue(issues, id, "至少配置一组执行人");
        for (var rule : objects(cfg.get("rules"))) {
          String source = text(rule, "source");
          if (!DirectoryService.SOURCES.contains(source)
            || !DirectoryService.SCOPES.contains(text(rule, "scope"))) issue(issues, id, "执行人来源或人员范围无效");
          if (Set.of("users", "roles", "organizations", "node", "variable").contains(source) && strings(rule.get("values")).isEmpty())
            issue(issues, id, "请选择执行人来源");
          if (source.equals("node") && !upstreamApprovals.containsAll(strings(rule.get("values"))))
            issue(issues, id, "执行人只能引用当前路径上游审批节点");
          if (source.equals("variable")) for (String variable : strings(rule.get("values")))
            validateBinding(Map.of("kind", "variable", "value", variable), upstream, id, issues);
          if (Set.of("users", "roles", "organizations").contains(source)) {
            String section = source.equals("users") ? "members" : source;
            Set<String> available = new HashSet<>(objects(directoryService.directory().get(section)).stream().map(item -> text(item, "id")).toList());
            if (!available.containsAll(strings(rule.get("values"))))
              issue(issues, id, "选择的用户、角色或组织已失效，请重新选择");
          }
        }
      }
      if (Set.of("create", "update", "query", "delete").contains(kind)) {
        AppFormResponse form = requireForm(appId, object(cfg.get("form")), id, issues);
        if (form != null) {
          Set<String> fields = new HashSet<>(form.fields().stream().map(item -> item.name()).toList());
          for (var condition : objects(cfg.get("conditions")))
            if (Set.of("isEmpty", "notEmpty").contains(text(condition, "operator")))
              issue(issues, id, "集合空值判断仅用于网关或触发条件");
          for (String section : List.of("assignments", "conditions"))
            for (var entry : objects(cfg.get(section))) {
              if (!fields.contains(text(entry, "field"))) issue(issues, id, "字段不存在: " + text(entry, "field"));
              if (section.equals("assignments") && Set.of("id", "created_by", "created_at", "updated_by", "updated_at").contains(text(entry, "field")))
                issue(issues, id, "不能赋值系统字段");
            }
        }
        if ((kind.equals("update") || kind.equals("delete")) && objects(cfg.get("conditions")).isEmpty())
          issue(issues, id, "更新和删除必须配置筛选条件");
        if ((kind.equals("create") || kind.equals("update")) && objects(cfg.get("assignments")).isEmpty())
          issue(issues, id, "请配置字段赋值");
      }
      if (kind.equals("subflow") && flows.stream().noneMatch(candidate -> text(candidate, "id").equals(text(cfg, "flowId"))))
        issue(issues, id, "请选择当前应用内的子流程");
      if (kind.equals("gateway")) {
        List<Map<String, Object>> branches = objects(node.get("blocks"));
        if (branches.size() < 2 || branches.stream().filter(branch -> Boolean.TRUE.equals(config(branch).get("fallback"))).count() != 1)
          issue(issues, id, "网关必须至少两个分支，且仅一个默认分支");
        for (var branch : branches) {
          if (!"branch".equals(text(branch, "type"))) issue(issues, id, "网关下只能包含条件分支");
          if (!Boolean.TRUE.equals(config(branch).get("fallback")) && objects(config(branch).get("conditions")).isEmpty())
            issue(issues, text(branch, "id"), "条件分支不能为空");
          validatePath(appId, List.of(branch), new LinkedHashSet<>(upstream), new LinkedHashSet<>(upstreamApprovals), ids, issues, flows);
        }
      } else if (kind.equals("branch"))
        validatePath(appId, objects(node.get("blocks")), new LinkedHashSet<>(upstream), new LinkedHashSet<>(upstreamApprovals), ids, issues, flows);
      if (kind.equals("branch")) for (var condition : objects(cfg.get("conditions")))
        validateBinding(Map.of("kind", "variable", "value", text(condition, "field")), upstream, id, issues);
      for (var entry : objects(cfg.get("assignments")))
        validateBinding(object(entry.get("value")), upstream, id, issues);
      for (var entry : objects(cfg.get("conditions"))) {
        validateBinding(object(entry.get("value")), upstream, id, issues);
        if (!ConditionOperator.valid(text(entry, "operator")) || text(entry, "field").isBlank())
          issue(issues, id, "条件字段或操作符无效");
        try {
          if (!"variable".equals(text(object(entry.get("value")), "kind"))) value(object(entry.get("value")), Map.of());
        } catch (RuntimeException error) {
          issue(issues, id, "条件值类型无效");
        }
      }
      if (kind.equals("subflow")) {
        for (var input : objects(cfg.get("inputs"))) validateBinding(object(input.get("value")), upstream, id, issues);
        for (String section : List.of("inputs", "outputs"))
          for (var mapping : objects(cfg.get(section))) {
            String field = text(mapping, "field");
            if (!field.matches("[A-Za-z][A-Za-z0-9_]{0,63}") || (section.equals("outputs") && Set.of("input", "record", "trigger", "context", "nodes", "initiator", "nodeMembers").contains(field)))
              issue(issues, id, "子流程映射变量无效或使用了保留名称");
            if (section.equals("outputs")) upstream.add(field);
          }
      }
      if (kind.equals("approval")) {
        upstream.add(id);
        upstreamApprovals.add(id);
      }
      upstream.add("nodes." + id);
      String output = text(cfg, "output");
      if (!output.isBlank()) {
        if (!output.matches("[A-Za-z][A-Za-z0-9_]{0,63}") || Set.of("record", "initiator", "nodeMembers", "__runId", "__rootRunId").contains(output) || upstream.contains(output))
          issue(issues, id, "输出变量无效、重复或使用了保留名称");
        upstream.add(output);
      }
    }
  }

  private void validateBinding(Map<String, Object> binding, Set<String> upstream, String id, List<Map<String, String>> issues) {
    if (!Set.of("literal", "variable").contains(text(binding, "kind"))) {
      issue(issues, id, "值绑定必须为 literal 或 variable");
      return;
    }
    if ("variable".equals(binding.get("kind"))) {
      String root = text(binding, "value").split("\\.")[0];
      if (root.equals("nodes") && text(binding, "value").contains(".") && !upstream.contains("nodes." + text(binding, "value").split("\\.")[1]))
        issue(issues, id, "节点输出不在当前上游路径");
      if (!upstream.contains(root))
        issue(issues, id, "变量不在当前路径上游范围内: " + root);
    }
  }

  private AppFormResponse requireForm(String appId, Map<String, Object> ref, String id, List<Map<String, String>> issues) {
    try {
      if (!"app".equals(text(ref, "kind"))) throw new IllegalArgumentException();
      var form = appFormService.findForm(appId, text(ref, "name"));
      if (form.builtin()) throw new IllegalArgumentException();
      return form;
    } catch (Exception error) {
      issue(issues, id, "请绑定当前应用的真实表单；示例表单不能发布");
      return null;
    }
  }

  private boolean cyclic(String id, Map<String, Object> flow, List<Map<String, Object>> flows, Set<String> visiting, Set<String> visited) {
    if (visiting.contains(id)) return true;
    if (!visited.add(id)) return false;
    visiting.add(id);
    for (var node : flatten(objects(object(flow.get("definition")).get("nodes"))))
      if (text(node, "type").equals("subflow")) {
        String child = text(config(node), "flowId");
        var candidate = flows.stream().filter(item -> text(item, "id").equals(child)).findFirst();
        if (candidate.isPresent() && cyclic(child, candidate.get(), flows, visiting, visited)) return true;
      }
    visiting.remove(id);
    return false;
  }

  private static void issue(List<Map<String, String>> issues, String id, String message) {
    issues.add(Map.of("nodeId", id, "message", message));
  }

  public FlowModel compile(Map<String, Object> flow, Map<String, Object> metadata) {
    FlowModel model = JsonUtils.parseToObject(text(AppFlowModel.encode(flow), "flow_model"), FlowModel.class);
    for (var element : model.getFlowElementList()) {
      var properties = element.getProperties();
      String kind = text(object(properties.get("config")), "kind");
      // Handler ownership is trusted server configuration, never copied from REST properties.
      if (element.getType() == 4) properties.put("handler", kind.equals("approval") ? "approval" : "app-flow");
      if (element.getType() == 2 || element == model.getFlowElementList().getFirst()) {
        properties.put("handlerOwner", "app-flow");
        properties.put("workflow", metadata);
      }
      properties.put("nodeId", element.getKey());
    }
    return model;
  }
}
