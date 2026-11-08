package dev.flexmodel.apps.flow;

import dev.flexmodel.apps.form.AppFormService;
import dev.flexmodel.JsonUtils;
import dev.flexmodel.directory.DirectoryService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Machine-readable authoring contract; examples use the same Flow codec as the designer.
 */
@ApplicationScoped
public class AppFlowCapabilities {
  @Inject
  AppFormService appFormService;
  @Inject
  AppFlowService appFlowService;

  public Map<String, Object> describe(String appId) {
    var availableForms = appFormService.findForms(appId).stream().filter(form -> !form.builtin()).toList();
    String formName = availableForms.isEmpty() ? "replace_with_form_name" : availableForms.getFirst().name();
    var form = Map.of("kind", "app", "name", formName);
    var rule = Map.of("id", "initiator", "source", "initiator", "values", List.of(), "scope", "self", "includeChildren", false);
    var approval = Map.of("kind", "approval", "signMode", "all", "rules", List.of(rule));
    var condition = Map.of("id", "has_record", "field", "record.id", "operator", "ne", "value", literal(""));
    var branch = node("matched", "匹配条件", Map.of("kind", "branch", "fallback", false, "match", "all", "conditions", List.of(condition)));
    branch.put("blocks", List.of(node("review", "审批", approval)));
    var fallback = node("fallback", "默认分支", Map.of("kind", "branch", "fallback", true, "match", "all", "conditions", List.of()));
    fallback.put("blocks", List.of());
    var gateway = node("route", "条件分流", Map.of("kind", "gateway"));
    gateway.put("blocks", List.of(branch, fallback));

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("schemaVersion", 2);
    result.put("flowContract", Map.of(
      "required", List.of("name", "model"),
      "optional", List.of("flowKey", "description"),
      "update", "Pass flowId and the latest revision returned by get_app_flow; flowKey stays stable",
      "model", "JSON object containing flowElementList; start config includes exactly one trigger",
      "statuses", List.of("draft", "enabled", "disabled"),
      "serverManaged", List.of("handler", "handlerOwner", "nodeId", "workflow"),
      "limits", Map.of("maxNodes", 200, "maxModelLength", 900000),
      "exampleFormMustBeReplaced", availableForms.isEmpty()));
    result.put("elementContract", Map.of(
      "required", List.of("key", "type", "incoming", "outgoing", "properties"),
      "types", Map.of("sequenceOrBranch", 1, "start", 2, "end", 3, "task", 4, "gateway", 6),
      "properties", Map.of(
        "name", "Node display title",
        "config", "Typed node config; kind is mandatory",
        "designer", "{parent: parent node key or empty string, order: zero-based sibling index, container: whether blocks exist}"),
      "connections", "incoming/outgoing reference element keys, including explicit type=1 sequence edges; topology must match designer ordering",
      "branch", "A type=1 conditional edge under a gateway; its children use the branch key as designer.parent; exactly one fallback per gateway"));
    result.put("nodeConfigs", Map.ofEntries(
      Map.entry("start", Map.of("kind", "start", "trigger", Map.of("type", "MANUAL", "formName", formName, "lockRecord", false))),
      Map.entry("end", Map.of("kind", "end")),
      Map.entry("approval", approval),
      Map.entry("notification", Map.of("kind", "notification", "rules", List.of(rule), "titleTemplate", "流程提醒", "contentTemplate", "记录 {{record.id}} 已更新")),
      Map.entry("gateway", Map.of("kind", "gateway")),
      Map.entry("branch", Map.of("kind", "branch", "fallback", false, "match", "all", "conditions", List.of(condition))),
      Map.entry("create", dataConfig("create", form, "created")),
      Map.entry("update", dataConfig("update", form, "updated")),
      Map.entry("query", dataConfig("query", form, "queried")),
      Map.entry("delete", dataConfig("delete", form, "deleted")),
      Map.entry("subflow", Map.of("kind", "subflow", "flowId", "replace_with_enabled_flow_module_id", "inputs", List.of(Map.of("id", "record_input", "field", "record", "value", Map.of("kind", "variable", "value", "record"))), "outputs", List.of()))));
    result.put("enums", Map.of("signMode", List.of("all", "any", "sequential"),
      "memberSource", DirectoryService.SOURCES, "memberScope", DirectoryService.SCOPES,
      "match", List.of("all", "any"), "operator", ConditionOperator.NAMES,
      "bindingKind", List.of("literal", "variable"), "valueType", List.of("text", "number", "boolean"),
      "queryMode", List.of("one", "many")));
    result.put("triggerTypes", List.of("MANUAL", "DATA_EVENT", "SCHEDULE"));
    result.put("triggers", List.of(Map.of("type", "MANUAL", "formName", formName, "lockRecord", false), Map.of("type", "DATA_EVENT", "formName", formName, "operations", List.of("CREATE", "UPDATE", "DELETE")), Map.of("type", "SCHEDULE", "cron", "0 0 9 * * ?", "timezone", "Asia/Shanghai")));
    result.put("forms", availableForms);
    result.put("subflows", appFlowService.list(appId).stream().map(flow -> Map.of(
      "id", flow.get("id"), "name", flow.get("name"), "status", flow.get("status"))).toList());
    result.put("examples", Map.of(
      "simple", example(formName, List.of(node("review", "审批", approval))),
      "gateway", example(formName, List.of(gateway))));
    result.put("rules", List.of(
      "Start must be first and end last; exactly one of each. Node keys must match [A-Za-z0-9_-]{1,100} and be unique.",
      "Use get_directory and resolve_members for real member IDs and selection previews; no scripts are supported.",
      "signMode all requires everyone, any requires one, sequential follows member order; any rejection ends the approval.",
      "Non-default branches need conditions; the first matching branch wins. Branch expression properties are generated by the server.",
      "Update/delete require conditions. Assignments cannot write system fields. Data nodes affect at most 1000 records and are not rolled back by later rejection.",
      "nodeConfigs are templates: replace form/field references and assignment values with actual form fields and their types. Keep IDs on rules, conditions and assignments for editing in the designer.",
      "Bindings use record.<field> for manual/data triggers, context, trigger, input or upstream nodes.<nodeId> outputs. Scheduled starts have no record or business data. Branch-local outputs cannot be referenced after the gateway.",
      "Subflows must belong to this app and be enabled before publishing their parent; cycles are rejected.",
      "Saving requires the start config to carry exactly one valid trigger from triggers (see triggerTypes); other parts may stay incomplete. Saving does not change the active deployment. Publish explicitly, then enable/disable independently.",
      "Publication does not submit records. Users submit explicitly from record details. Runtime requires a SQL project database."));
    return result;
  }

  private Map<String, Object> example(String formName, List<Map<String, Object>> tasks) {
    var nodes = new java.util.ArrayList<Map<String, Object>>();
    nodes.add(node("start", "开始", Map.of("kind", "start", "trigger", Map.of("type", "MANUAL", "formName", formName, "lockRecord", false))));
    nodes.addAll(tasks);
    nodes.add(node("end", "结束", Map.of("kind", "end")));
    return JsonUtils.parseToMap(JsonUtils.toJsonString(AppFlowModel.api(Map.of("name", "审批流程", "description", "",
      "form", Map.of("kind", "app", "name", formName), "definition", Map.of("nodes", nodes)))));
  }

  private Map<String, Object> node(String id, String title, Map<String, ?> config) {
    return new LinkedHashMap<>(Map.of("id", id, "type", config.get("kind"), "data", Map.of("title", title, "config", config)));
  }

  private Map<String, Object> literal(String value) {
    return Map.of("kind", "literal", "valueType", "text", "value", value);
  }

  private Map<String, Object> recordCondition() {
    return Map.of("id", "record_id", "field", "id", "operator", "eq", "value", Map.of("kind", "variable", "value", "record.id"));
  }

  private Map<String, Object> dataConfig(String kind, Map<String, String> form, String output) {
    boolean assigns = kind.equals("create") || kind.equals("update");
    return Map.of("kind", kind, "form", form, "match", "all", "queryMode", "many", "output", output,
      "conditions", kind.equals("create") ? List.of() : List.of(recordCondition()),
      "assignments", assigns ? List.of(Map.of("id", "name_assignment", "field", "name", "value", literal("Example"))) : List.of());
  }
}
