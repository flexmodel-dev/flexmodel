package dev.flexmodel.approval;

import java.util.*;

import static dev.flexmodel.approval.ApprovalJson.*;
import static dev.flexmodel.common.Values.*;

/**
 * Predicts the linear remainder of a running approval from its deployed flow snapshot.
 * A gateway whose conditions cannot be decided with the current variables stops the walk and
 * returns its branches as candidates, because the real decision happens when the flow reaches it.
 */
public final class ApprovalForecast {
  private static final int MAX_NODES = 12;
  private static final Map<String, String> DEFAULT_TITLES = Map.of(
    "approval", "审批", "notification", "通知", "create", "新增数据", "update", "更新数据",
    "query", "查询数据", "delete", "删除数据", "subflow", "子流程", "end", "流程结束", "gateway", "条件分流");

  private ApprovalForecast() {
  }

  public static List<Map<String, Object>> forecast(Map<String, Object> flow, Collection<String> currentKeys,
                                                   Map<String, Object> variables) {
    var nodes = objects(object(flow.get("definition")).get("nodes"));
    var values = variables == null ? Map.<String, Object>of() : variables;
    var result = new LinkedHashMap<String, Map<String, Object>>();
    for (var key : currentKeys) {
      var path = path(nodes, key);
      if (path != null) walk(path, values, result);
    }
    return new ArrayList<>(result.values());
  }

  private static void walk(List<Frame> path, Map<String, Object> variables, Map<String, Map<String, Object>> result) {
    var stack = new ArrayDeque<Frame>();
    for (var frame : path) stack.addLast(new Frame(frame.nodes, frame.index + 1));
    while (!stack.isEmpty()) {
      var frame = stack.peekLast();
      if (frame.index >= frame.nodes.size()) {
        stack.removeLast();
        continue;
      }
      var node = frame.nodes.get(frame.index++);
      String kind = text(node, "type");
      if (kind.equals("start") || kind.equals("branch")) continue;
      if (kind.equals("gateway")) {
        var branch = choose(node, variables);
        if (branch == null) {
          result.putIfAbsent(text(node, "id"), branches(node));
          return;
        }
        stack.addLast(new Frame(objects(branch.get("blocks")), 0));
        continue;
      }
      if (result.size() >= MAX_NODES) return;
      result.putIfAbsent(text(node, "id"), view(node));
      if (kind.equals("end")) return;
    }
  }

  private static Map<String, Object> choose(Map<String, Object> gateway, Map<String, Object> variables) {
    Map<String, Object> fallback = null;
    for (var branch : objects(gateway.get("blocks"))) {
      var config = config(branch);
      if (Boolean.TRUE.equals(config.get("fallback"))) {
        fallback = branch;
        continue;
      }
      if (!decidable(config, variables)) return null;
      if (ApprovalRecordActions.matches(config, variables)) return branch;
    }
    return fallback;
  }

  /**
   * Conditions that reference values not produced yet cannot pick a branch; they stay candidates.
   */
  private static boolean decidable(Map<String, Object> config, Map<String, Object> variables) {
    for (var condition : objects(config.get("conditions"))) {
      if (!present(variables, text(condition, "field"))) return false;
      var value = object(condition.get("value"));
      if ("variable".equals(text(value, "kind")) && !present(variables, text(value, "value"))) return false;
    }
    return true;
  }

  private static boolean present(Map<String, Object> data, String path) {
    if (path.isBlank()) return false;
    Object current = data;
    for (var part : path.split("\\.")) {
      if (current instanceof Map<?, ?> map) {
        if (!map.containsKey(part)) return false;
        current = map.get(part);
      } else if (current instanceof List<?> list && part.matches("\\d+") && Integer.parseInt(part) < list.size()) {
        current = list.get(Integer.parseInt(part));
      } else return false;
    }
    return true;
  }

  private static Map<String, Object> view(Map<String, Object> node) {
    var view = new LinkedHashMap<String, Object>();
    view.put("id", text(node, "id"));
    view.put("title", title(node));
    view.put("kind", text(node, "type"));
    if (text(node, "type").equals("approval")) view.put("sign_mode", text(config(node), "signMode"));
    view.put("certain", true);
    return view;
  }

  private static Map<String, Object> branches(Map<String, Object> gateway) {
    var view = new LinkedHashMap<String, Object>();
    view.put("id", text(gateway, "id"));
    view.put("title", title(gateway));
    view.put("kind", "gateway");
    view.put("certain", false);
    view.put("candidates", objects(gateway.get("blocks")).stream().map(ApprovalForecast::first)
      .filter(Objects::nonNull).toList());
    return view;
  }

  private static Map<String, Object> first(Map<String, Object> branch) {
    for (var node : objects(branch.get("blocks")))
      if (!text(node, "type").equals("start") && !text(node, "type").equals("branch")) return view(node);
    return null;
  }

  private static String title(Map<String, Object> node) {
    String title = text(object(node.get("data")), "title");
    return title.isBlank() ? DEFAULT_TITLES.getOrDefault(text(node, "type"), "未命名节点") : title;
  }

  private static List<Frame> path(List<Map<String, Object>> nodes, String target) {
    for (int i = 0; i < nodes.size(); i++) {
      var node = nodes.get(i);
      if (target.equals(text(node, "id"))) return new ArrayList<>(List.of(new Frame(nodes, i)));
      for (var branch : objects(node.get("blocks"))) {
        var inner = path(objects(branch.get("blocks")), target);
        if (inner != null) {
          inner.add(0, new Frame(nodes, i));
          return inner;
        }
      }
    }
    return null;
  }

  private static final class Frame {
    private final List<Map<String, Object>> nodes;
    private int index;

    private Frame(List<Map<String, Object>> nodes, int index) {
      this.nodes = nodes;
      this.index = index;
    }
  }
}
