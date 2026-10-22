package dev.flexmodel.approval;

import dev.flexmodel.common.ValidationException;
import dev.flexmodel.flow.common.util.impl.JavaScriptExpressionCalculator;

import java.util.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static dev.flexmodel.approval.ApprovalJson.*;

class ApprovalFlowModelTest {
  Map<String, Object> node(String id, String kind) {
    return new LinkedHashMap<>(Map.of("id", id, "type", kind, "data", Map.of("title", id, "config", Map.of("kind", kind))));
  }

  Map<String, Object> draft() {
    var gateway = node("gateway", "gateway");
    List<Map<String, Object>> branches = new ArrayList<>();
    for (String id : List.of("conditional", "fallback")) {
      var branch = node(id, "branch");
      branch.put("data", Map.of("title", id, "config", Map.of("kind", "branch", "fallback", id.equals("fallback"), "match", "all", "conditions", List.of())));
      branch.put("blocks", id.equals("conditional") ? List.of(node("review", "approval")) : List.of());
      branches.add(branch);
    }
    gateway.put("blocks", branches);
    return new LinkedHashMap<>(Map.of("id", "flow", "name", "测试", "status", "draft", "description", "备注", "form", Map.of("kind", "app", "name", "expense"),
      "definition", Map.of("nodes", List.of(node("start", "start"), gateway, node("end", "end")))));
  }

  @Test
  void roundTripPreservesNestedEmptyBranchesAndUsesNativeGateway() {
    var source = draft();
    var record = ApprovalFlowModel.encode(source);
    var restored = ApprovalFlowModel.decode(record);
    assertEquals(source.get("definition"), restored.get("definition"));
    assertFalse(record.containsKey("definition"));
    assertInstanceOf(String.class, record.get("flow_model"));
    var model = new ApprovalCompiler().compile(restored, Map.of("revision", 1));
    var gateway = model.getFlowElementList().stream().filter(e -> e.getKey().equals("gateway")).findFirst().orElseThrow();
    assertEquals(6, gateway.getType());
    assertFalse(gateway.getProperties().containsKey("handler"));
    assertEquals(List.of("conditional", "fallback"), gateway.getOutgoing());
  }

  @Test
  void rejectsInvalidJsonAndConflictingConnectivity() {
    assertThrows(ValidationException.class, () -> ApprovalFlowModel.decode(Map.of("flow_model", "{")));
    var record = ApprovalFlowModel.encode(draft());
    record.put("flow_model", text(record, "flow_model").replace("sequence_start", "broken_edge"));
    assertThrows(ValidationException.class, () -> ApprovalFlowModel.decode(record));
  }

  @Test
  void nativeExpressionsMatchTypedBusinessConditions() throws Exception {
    var calculator = new JavaScriptExpressionCalculator();
    for (String operator : List.of("eq", "ne", "gt", "gte", "lt", "lte", "contains")) {
      for (String binding : List.of("literal", "variable")) {
        var condition = Map.<String, Object>of("field", "record.amount", "operator", operator, "value", Map.of("kind", binding, "value", binding.equals("literal") ? "2" : "limit", "valueType", "number"));
        var config = Map.<String, Object>of("conditions", List.of(condition), "match", "all");
        for (var record : List.of(Map.of("amount", 3), Map.of("amount", 2), Map.of())) {
          var variables = Map.<String, Object>of("record", record, "limit", 2);
          assertEquals(ApprovalRecordActions.matches(config, variables), calculator.calculate(ApprovalFlowModel.condition(config), variables), operator + ":" + binding + ":" + record);
        }
      }
    }
    var config = Map.<String, Object>of("conditions", List.of(Map.of("field", "amount", "operator", "eq", "value", Map.of("kind", "literal", "value", "2", "valueType", "number"))), "match", "all");
    assertTrue(calculator.calculate(ApprovalFlowModel.condition(config), Map.of("amount", 2)));
    assertFalse(calculator.calculate(ApprovalFlowModel.condition(config), Map.of()), "不能读取同线程上一次计算残留的变量");
  }
}
