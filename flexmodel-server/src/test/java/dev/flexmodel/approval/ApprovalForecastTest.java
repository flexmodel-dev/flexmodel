package dev.flexmodel.approval;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dev.flexmodel.common.Values.*;
import static org.junit.jupiter.api.Assertions.*;

class ApprovalForecastTest {

  @Test
  void predictsLinearNodesUntilEnd() {
    var flow = flow(List.of(
      node("start", "start", "发起", Map.of("kind", "start")),
      node("review", "approval", "主管审批", Map.of("kind", "approval", "signMode", "all")),
      node("notify", "notification", "通知申请人", Map.of("kind", "notification")),
      node("end", "end", "", Map.of("kind", "end"))
    ));

    var result = ApprovalForecast.forecast(flow, List.of("review"), Map.of("record", Map.of()));

    assertEquals(List.of("notify", "end"), ids(result));
    assertEquals("notification", text(result.getFirst(), "kind"));
    assertEquals(Boolean.TRUE, result.getFirst().get("certain"));
    assertEquals("流程结束", text(result.getLast(), "title"));
  }

  @Test
  void followsEvaluatedBranchAndJoinsAfterGateway() {
    var flow = expenseFlow();

    var high = ApprovalForecast.forecast(flow, List.of("review"), Map.of("record", Map.of("amount", 5000)));
    assertEquals(List.of("finance", "end"), ids(high));
    assertEquals("any", text(high.getFirst(), "sign_mode"));

    var low = ApprovalForecast.forecast(flow, List.of("review"), Map.of("record", Map.of("amount", 100)));
    assertEquals(List.of("notify", "end"), ids(low));
  }

  @Test
  void returnsCandidatesWhenVariablesCannotDecideGateway() {
    var result = ApprovalForecast.forecast(expenseFlow(), List.of("review"), Map.of());

    assertEquals(1, result.size());
    var gateway = result.getFirst();
    assertEquals("route", text(gateway, "id"));
    assertEquals(Boolean.FALSE, gateway.get("certain"));
    assertEquals(List.of("finance", "notify"), ids(objects(gateway.get("candidates"))));
  }

  @Test
  void ignoresUnknownCurrentNode() {
    assertTrue(ApprovalForecast.forecast(expenseFlow(), List.of("missing"), Map.of()).isEmpty());
    assertTrue(ApprovalForecast.forecast(expenseFlow(), List.of(), Map.of()).isEmpty());
  }

  private static Map<String, Object> expenseFlow() {
    return flow(List.of(
      node("start", "start", "发起", Map.of("kind", "start")),
      node("review", "approval", "主管审批", Map.of("kind", "approval", "signMode", "all")),
      gateway("route", "条件分流", List.of(
        branch("high", "金额较大", false, List.of(condition("record.amount", "gt", "1000")),
          List.of(node("finance", "approval", "财务审批", Map.of("kind", "approval", "signMode", "any")))),
        branch("fallback", "默认分支", true, List.of(),
          List.of(node("notify", "notification", "通知申请人", Map.of("kind", "notification"))))
      )),
      node("end", "end", "", Map.of("kind", "end"))
    ));
  }

  private static List<String> ids(List<Map<String, Object>> nodes) {
    return nodes.stream().map(node -> text(node, "id")).toList();
  }

  private static Map<String, Object> flow(List<Map<String, Object>> nodes) {
    return new LinkedHashMap<>(Map.of("definition", new LinkedHashMap<>(Map.of("nodes", nodes))));
  }

  private static Map<String, Object> node(String id, String type, String title, Map<String, Object> config) {
    return new LinkedHashMap<>(Map.of("id", id, "type", type, "data", Map.of("title", title, "config", config)));
  }

  private static Map<String, Object> container(String id, String type, String title,
                                               Map<String, Object> config, List<Map<String, Object>> blocks) {
    var node = node(id, type, title, config);
    node.put("blocks", blocks);
    return node;
  }

  private static Map<String, Object> gateway(String id, String title, List<Map<String, Object>> branches) {
    return container(id, "gateway", title, Map.of("kind", "gateway"), branches);
  }

  private static Map<String, Object> branch(String id, String title, boolean fallback,
                                            List<Map<String, Object>> conditions, List<Map<String, Object>> blocks) {
    var config = new LinkedHashMap<String, Object>();
    config.put("kind", "branch");
    config.put("fallback", fallback);
    config.put("match", "all");
    config.put("conditions", conditions);
    return container(id, "branch", title, config, blocks);
  }

  private static Map<String, Object> condition(String field, String operator, String value) {
    return Map.of("field", field, "operator", operator,
      "value", Map.of("kind", "literal", "valueType", "number", "value", value));
  }
}
