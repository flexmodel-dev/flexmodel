package dev.flexmodel.apps.flow;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.apps.form.AppFormService;
import dev.flexmodel.apps.form.dto.AppFormFieldResponse;
import dev.flexmodel.apps.form.dto.AppFormRecord;
import dev.flexmodel.apps.form.dto.AppFormResponse;
import dev.flexmodel.directory.DirectoryService;
import dev.flexmodel.flow.common.util.impl.JavaScriptExpressionCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipInputStream;

import static dev.flexmodel.common.Values.*;
import static org.junit.jupiter.api.Assertions.*;

class AppFlowTemplateTest {
  private final AppFlowCompiler compiler = new AppFlowCompiler();
  private List<Map<String, Object>> flows;

  @BeforeEach
  void loadErpTemplate() throws Exception {
    Map<String, String> files = new HashMap<>();
    try (var input = getClass().getResourceAsStream("/app-templates/erp.flexmodel.zip")) {
      assertNotNull(input);
      try (var zip = new ZipInputStream(input)) {
        for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
          if (Set.of("flows.json", "forms.json").contains(entry.getName()))
            files.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
        }
      }
    }
    flows = JsonUtils.parseToMapList(files.get("flows.json"));
    Map<String, AppFormResponse> forms = new HashMap<>();
    for (var form : JsonUtils.parseToList(files.get("forms.json"), AppFormRecord.class)) {
      var fields = form.fields().stream().map(field -> new AppFormFieldResponse(
        field.name(), field.label(), field.type(), field.required(), field.builtin(),
        field.options(), field.referenceForm())).toList();
      forms.put(form.name(), new AppFormResponse(form.name(), form.label(), form.description(),
        form.displayField(), form.searchField(), true, false, null, null, fields, null, List.of()));
    }
    compiler.appFormService = new AppFormService() {
      @Override
      public AppFormResponse findForm(String appId, String formName) {
        return Objects.requireNonNull(forms.get(formName));
      }
    };
    compiler.directoryService = new DirectoryService() {
      @Override
      public Map<String, Object> directory() {
        return Map.of("members", List.of(Map.of("id", "admin")),
          "roles", List.of(Map.of("id", "admin"), Map.of("id", "developer")), "organizations", List.of());
      }
    };
  }

  @Test
  void erpTemplateFlowsPassRuntimeValidation() {
    assertFalse(flows.isEmpty());
    for (var flow : flows)
      assertEquals(List.of(), compiler.validate("erp", flow, flows), text(flow, "flowKey"));
  }

  @Test
  void transferBranchesCompareWarehouseIdsInBusinessAndNativeConditions() throws Exception {
    var flow = flows.stream().filter(item -> "erp_transfer_approval".equals(text(item, "flowKey"))).findFirst().orElseThrow();
    var nodes = AppFlowJson.flatten(objects(object(flow.get("definition")).get("nodes")));
    var model = compiler.compile(flow, Map.of());
    var calculator = new JavaScriptExpressionCalculator();
    for (var scenario : List.of(
      Map.of("to", "warehouse-a", "amount", 60000, "branch", "tr_same"),
      Map.of("to", "warehouse-b", "amount", 60000, "branch", "tr_cross"),
      Map.of("to", "warehouse-b", "amount", 50000, "branch", "tr_regular"))) {
      var variables = Map.<String, Object>of("record", Map.of("from_warehouse", "warehouse-a",
        "to_warehouse", scenario.get("to"), "total_amount", scenario.get("amount")));
      for (String id : List.of("tr_cross", "tr_same")) {
        var node = nodes.stream().filter(item -> id.equals(text(item, "id"))).findFirst().orElseThrow();
        var edge = model.getFlowElementList().stream().filter(item -> id.equals(item.getKey())).findFirst().orElseThrow();
        boolean expected = id.equals(scenario.get("branch"));
        assertEquals(expected, AppFlowRecordActions.matches(AppFlowJson.config(node), variables), id + scenario);
        assertEquals(expected, calculator.calculate(text(edge.getProperties(), "conditionsequenceflow"), variables), id + scenario);
      }
    }
  }
}
