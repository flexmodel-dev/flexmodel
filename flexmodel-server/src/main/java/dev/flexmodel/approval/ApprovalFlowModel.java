package dev.flexmodel.approval;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.common.ValidationException;

import java.util.*;

import static dev.flexmodel.approval.ApprovalJson.*;

/**
 * Flow is the persisted model. Tree objects exist only as an editor/business-service view.
 */
public final class ApprovalFlowModel {
  private ApprovalFlowModel() {
  }

  public static Map<String, Object> metadata(Map<String, Object> row) {
    return objects(JsonUtils.parseToMap(text(row, "flow_model")).get("flowElementList")).stream()
      .map(element -> {
        var properties = object(element.get("properties"));
        return object(properties.get("approval"));
      })
      .filter(value -> !value.isEmpty()).findFirst().orElseGet(LinkedHashMap::new);
  }

  public static boolean managed(Map<String, Object> row) {
    if (text(row, "flow_model").isBlank()) return false;
    try {
      return objects(JsonUtils.parseToMap(text(row, "flow_model")).get("flowElementList")).stream()
        .anyMatch(element -> "approval-flow".equals(text(object(element.get("properties")), "handlerOwner")));
    } catch (RuntimeException error) {
      return false;
    }
  }

  public static Map<String, Object> encode(Map<String, Object> flow) {
    Map<String, Map<String, Object>> graph = new LinkedHashMap<>();
    List<Map<String, Object>> nodes = objects(object(flow.get("definition")).get("nodes"));
    index(nodes, "", graph);
    connect(nodes, null, graph);
    if (!nodes.isEmpty())
      object(graph.get(text(nodes.getFirst(), "id")).get("properties")).put("form", flow.get("form"));
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("flow_module_id", text(flow, "id"));
    result.put("flow_name", text(flow, "name"));
    result.put("flow_key", flow.getOrDefault("flowKey", text(flow, "id").isEmpty() ? "" : "approval_" + text(flow, "id")));
    result.put("flow_model", JsonUtils.toJsonString(Map.of("flowElementList", graph.values())));
    result.put("status", switch (text(flow, "status")) {
      case "enabled" -> 2;
      case "disabled" -> 3;
      default -> 1;
    });
    result.put("remark", text(flow, "description"));
    for (String key : List.of("revision", "archive", "is_deleted", "created_by", "updated_by", "created_at", "updated_at"))
      if (flow.containsKey(key)) result.put(key, flow.get(key));
    return result;
  }

  private static void index(List<Map<String, Object>> nodes, String parent, Map<String, Map<String, Object>> graph) {
    for (int i = 0; i < nodes.size(); i++) {
      var node = nodes.get(i);
      String id = text(node, "id"), kind = text(node, "type");
      if (id.isBlank() || graph.containsKey(id)) throw new ValidationException("节点标识无效或重复");
      var properties = new LinkedHashMap<String, Object>();
      properties.put("name", text(object(node.get("data")), "title"));
      properties.put("config", config(node));
      properties.put("designer", Map.of("parent", parent, "order", i, "container", node.containsKey("blocks")));
      var element = new LinkedHashMap<String, Object>();
      element.put("key", id);
      element.put("type", switch (kind) {
        case "start" -> 2;
        case "end" -> 3;
        case "gateway" -> 6;
        case "branch" -> 1;
        default -> 4;
      });
      element.put("incoming", new ArrayList<String>());
      element.put("outgoing", new ArrayList<String>());
      element.put("properties", properties);
      graph.put(id, element);
      index(objects(node.get("blocks")), id, graph);
    }
  }

  private static String connect(List<Map<String, Object>> nodes, String next, Map<String, Map<String, Object>> graph) {
    String target = next;
    for (int i = nodes.size() - 1; i >= 0; i--) {
      var node = nodes.get(i);
      String id = text(node, "id");
      if (text(node, "type").equals("gateway")) {
        for (var branch : objects(node.get("blocks"))) {
          String first = connect(objects(branch.get("blocks")), target, graph);
          var edge = graph.get(text(branch, "id"));
          link(graph.get(id), edge);
          if (first != null) link(edge, graph.get(first));
          var properties = object(edge.get("properties"));
          boolean fallback = Boolean.TRUE.equals(config(branch).get("fallback"));
          properties.put("defaultConditions", Boolean.toString(fallback));
          properties.put("conditionsequenceflow", fallback ? "" : condition(config(branch)));
        }
      } else if (target != null) {
        String edgeId = "sequence_" + id;
        if (graph.containsKey(edgeId)) throw new ValidationException("节点标识与连线标识冲突");
        var edge = new LinkedHashMap<String, Object>();
        edge.put("key", edgeId);
        edge.put("type", 1);
        edge.put("incoming", new ArrayList<String>());
        edge.put("outgoing", new ArrayList<String>());
        edge.put("properties", Map.of("conditionsequenceflow", "", "defaultConditions", "false"));
        graph.put(edgeId, edge);
        link(graph.get(id), edge);
        link(edge, graph.get(target));
      }
      target = id;
    }
    return target;
  }

  @SuppressWarnings("unchecked")
  private static void link(Map<String, Object> source, Map<String, Object> target) {
    ((List<String>) source.get("outgoing")).add(text(target, "key"));
    ((List<String>) target.get("incoming")).add(text(source, "key"));
  }

  // The same typed comparison and null semantics as ApprovalRecordActions.matches.
  public static String condition(Map<String, Object> config) {
    return "(() => { const cfg = " + JsonUtils.toJsonString(config) + "; const vars = __flowVariables; "
      + "const path = p => p.split('.').reduce((v,k) => v == null ? null : v[k], vars) ?? null; "
      + "const results = (cfg.conditions || []).map(c => { const l = path(c.field); "
      + "const b=c.value, r=b.kind==='variable'?path(b.value):b.valueType==='number'?Number(b.value):b.valueType==='boolean'?b.value.toLowerCase()==='true':b.value; "
      + "const cmp=typeof l==='number'&&typeof r==='number'?l-r:l!=null&&r!=null?(String(l)>String(r)?1:String(l)<String(r)?-1:0):0; "
      + "const eq=JSON.stringify(l)===JSON.stringify(r); switch(c.operator){case 'eq':return eq;case 'ne':return !eq;"
      + "case 'gt':return l!=null&&cmp>0;case 'gte':return l!=null&&cmp>=0;case 'lt':return l!=null&&cmp<0;case 'lte':return l!=null&&cmp<=0;"
      + "case 'contains':return Array.isArray(l)?l.some(v=>JSON.stringify(v)===JSON.stringify(r)):l!=null&&String(l).includes(r==null?'':String(r));default:return false;} }); "
      + "return cfg.match==='any'?results.some(Boolean):results.every(Boolean); })()";
  }

  public static Map<String, Object> decode(Map<String, Object> row) {
    try {
      String json = text(row, "flow_model");
      if (json.length() > 900000) throw new ValidationException("流程配置超过 900KB");
      List<Map<String, Object>> elements = objects(JsonUtils.parseToMap(json).get("flowElementList"));
      Map<String, Map<String, Object>> nodes = new LinkedHashMap<>();
      Map<String, List<Map<String, Object>>> groups = new HashMap<>();
      Map<String, Object> form = Map.of();
      for (var element : elements) {
        var properties = object(element.get("properties"));
        var cfg = object(properties.get("config"));
        if (cfg.isEmpty()) continue;
        String key = text(element, "key"), kind = text(cfg, "kind");
        if (nodes.containsKey(key)) throw new ValidationException("节点标识重复");
        var node = new LinkedHashMap<String, Object>(Map.of("id", key, "type", kind, "data", Map.of("title", text(properties, "name"), "config", cfg)));
        if (Boolean.TRUE.equals(object(properties.get("designer")).get("container"))) node.put("blocks", List.of());
        nodes.put(key, node);
        groups.computeIfAbsent(text(object(properties.get("designer")), "parent"), ignored -> new ArrayList<>()).add(element);
        if (kind.equals("start")) form = object(properties.get("form"));
      }
      if (nodes.isEmpty() || nodes.size() > 200)
        throw new ValidationException("审批模型需要节点 config 和 designer 配置，最多 200 个节点");
      for (var entry : groups.entrySet()) {
        entry.getValue().sort(Comparator.comparingInt(e -> ((Number) object(object(e.get("properties")).get("designer")).getOrDefault("order", 0)).intValue()));
        if (!entry.getKey().isEmpty()) {
          var parent = nodes.get(entry.getKey());
          if (parent == null) throw new ValidationException("设计器父节点不存在");
          parent.put("blocks", entry.getValue().stream().map(e -> nodes.get(text(e, "key"))).toList());
        }
      }
      var roots = groups.getOrDefault("", List.of()).stream().map(e -> nodes.get(text(e, "key"))).toList();
      // Check parent cycles/disconnected components before traversing the tree.
      Set<String> visited = new HashSet<>();
      visit(roots, visited);
      if (visited.size() != nodes.size()) throw new ValidationException("节点层级存在循环或孤立节点");
      Map<String, Object> flow = new LinkedHashMap<>();
      flow.put("id", text(row, "flow_module_id"));
      flow.put("name", text(row, "flow_name"));
      flow.put("flowKey", text(row, "flow_key"));
      flow.put("description", text(row, "remark"));
      flow.put("status", switch (((Number) row.getOrDefault("status", 1)).intValue()) {
        case 2 -> "enabled";
        case 3 -> "disabled";
        default -> "draft";
      });
      flow.put("form", form);
      flow.put("definition", Map.of("nodes", roots));
      for (String key : List.of("revision", "archive", "is_deleted", "created_by", "updated_by", "created_at", "updated_at"))
        if (row.containsKey(key)) flow.put(key, row.get(key));
      flow.put("updatedAt", row.getOrDefault("updated_at", ""));
      // Reject conflicting graph/tree metadata, rather than silently executing a different graph.
      var expected = objects(JsonUtils.parseToMap(text(encode(flow), "flow_model")).get("flowElementList"));
      if (!topology(elements).equals(topology(expected)))
        throw new ValidationException("流程连线与设计器节点配置不一致");
      return flow;
    } catch (ValidationException error) {
      throw error;
    } catch (RuntimeException error) {
      throw new ValidationException("flow_model 必须是有效的 Flow JSON 字符串");
    }
  }

  private static void visit(List<Map<String, Object>> nodes, Set<String> visited) {
    for (var node : nodes) {
      if (!visited.add(text(node, "id"))) throw new ValidationException("节点层级存在循环");
      visit(objects(node.get("blocks")), visited);
    }
  }

  private static Map<String, Object> topology(List<Map<String, Object>> elements) {
    Map<String, Object> result = new HashMap<>();
    for (var e : elements)
      if (result.put(text(e, "key"), List.of(e.get("type"), e.get("incoming"), e.get("outgoing"))) != null)
        throw new ValidationException("元素标识重复");
    return result;
  }
}
