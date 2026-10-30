package dev.flexmodel.mcp;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.SQLiteTestResource;
import dev.flexmodel.apps.AppFormService;
import dev.flexmodel.apps.AppRecordService;
import dev.flexmodel.apps.AppService;
import dev.flexmodel.apps.dto.*;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AppTools 集成测试。
 */
@QuarkusTest
@QuarkusTestResource(SQLiteTestResource.class)
class AppToolsTest {

  private static final String SUFFIX = Long.toHexString(System.nanoTime());

  @Inject
  AppTools appTools;

  @Inject
  AppService appService;

  @Inject
  AppFormService appFormService;

  @Inject
  AppRecordService appRecordService;

  @Test
  void createsCompleteAppChain() {
    String appId = "app_tools_" + SUFFIX;
    String formName = "orders_" + SUFFIX;
    try {
      AppResponse app = JsonUtils.parseToObject(appTools.create_app(
          "订单管理-" + SUFFIX, "MCP AppTools 集成测试", appId, "ShoppingCart", "indigo"),
        AppResponse.class);
      assertEquals(appId, app.id());
      assertEquals(appId, app.projectId());

      AppFormResponse form = JsonUtils.parseToObject(appTools.create_app_form(
        appId, "订单", formName, "订单表单", true, """
          [
            {"label":"订单金额","name":"order_amount","type":"DECIMAL","required":true}
          ]
          """), AppFormResponse.class);
      assertEquals(formName, form.name());
      String amountName = fieldByName(form, "订单金额").name();
      assertEquals("order_amount", amountName);
      assertEquals(2, form.layout().items().size());

      AppFormResponse savedForm = JsonUtils.parseToObject(appTools.save_app_form_layout(
        appId, formName, """
          [
            {"type":"SECTION","label":"订单信息","width":"FULL"},
            {"type":"FIELD","name":"name","width":"HALF"},
            {"type":"FIELD","name":"%s","width":"HALF"}
          ]
          """.formatted(amountName)), AppFormResponse.class);
      assertEquals(3, savedForm.layout().items().size());
      assertEquals("FULL", savedForm.layout().items().getFirst().width());

      var nav = JsonUtils.parseToObject(appTools.create_app_navigation(
          appId, "ENTRY", "订单", "ShoppingCart", null, "FORM", formName),
        dev.flexmodel.apps.dto.AppNavResponse.class);
      assertEquals("FORM", nav.destination().type());
      assertEquals(formName, nav.destination().id());
      assertNotNull(nav.pageRoute());

      List<Map<String, Object>> records = JsonUtils.parseToMapList(appTools.create_app_mock_records(
        appId, formName, """
          [
            {"name":"订单一","%s":100},
            {"name":"订单二","%s":250}
          ]
          """.formatted(amountName, amountName), null, null, null));
      assertEquals(2, records.size());
      records.forEach(record -> assertNotNull(record.get("id")));

      AppFormResponse finalForm = JsonUtils.parseToObject(
        appTools.get_app_form(appId, formName), AppFormResponse.class);
      assertEquals(savedForm.layout(), finalForm.layout());
    } finally {
      deleteAppQuietly(appId);
    }
  }

  @Test
  void createsAiFriendlyWorkbenchAndNavLink() {
    String appId = "app_tools_workbench_" + SUFFIX;
    String formName = "workbench_form_" + SUFFIX;
    try {
      createApp(appId);
      AppFormResponse form = JsonUtils.parseToObject(appTools.create_app_form(
        appId, "订单", formName, "", true, """
          [
            {"label":"订单金额","type":"DECIMAL","required":true},
            {"label":"客户","type":"TEXT","required":true}
          ]
          """), AppFormResponse.class);
      String amountName = fieldByName(form, "订单金额").name();
      String customerName = fieldByName(form, "客户").name();

      WorkbenchCapabilityResponse capability = JsonUtils.parseToObject(
        appTools.get_app_workbench_capabilities(appId), WorkbenchCapabilityResponse.class);
      assertEquals(1, capability.schemaVersion());
      assertTrue(capability.forms().stream().anyMatch(item -> item.name().equals(formName)));

      WorkbenchSummaryResponse[] workbenches = JsonUtils.parseToObject(
        appTools.list_app_workbenches(appId), WorkbenchSummaryResponse[].class);
      String workbenchId = workbenches[0].id();
      WorkbenchResponse saved = JsonUtils.parseToObject(appTools.save_app_workbench_layout(
        appId, workbenchId, """
          {
            "schemaVersion": 1,
            "components": [
              {
                "id": "metric",
                "title": "核心指标",
                "placement": {"x": 0, "y": 0, "w": 3, "h": 3},
                "config": {"type": "metric", "formName": "%s", "aggregation": "count", "field": null}
              },
              {
                "id": "chart",
                "title": "客户指标",
                "placement": {"x": 3, "y": 0, "w": 6, "h": 4},
                "config": {
                  "type": "chart",
                  "chartType": "bar",
                  "formName": "%s",
                  "dimensionField": "%s",
                  "measureField": "%s",
                  "aggregation": "sum",
                  "showLegend": true,
                  "showLabel": true,
                  "smooth": false,
                  "palette": "primary"
                }
              },
              {
                "id": "records",
                "title": "订单明细",
                "placement": {"x": 0, "y": 4, "w": 12, "h": 5},
                "config": {
                  "type": "dataloader",
                  "formName": "%s",
                  "visibleColumns": ["id", "name", "%s", "%s"]
                }
              },
              {
                "id": "tasks",
                "title": "我的任务",
                "placement": {"x": 9, "y": 0, "w": 3, "h": 3},
                "config": {"type": "tasks"}
              }
            ]
          }
          """.formatted(formName, formName, customerName, amountName, formName,
          customerName, amountName)), WorkbenchResponse.class);
      assertEquals(4, saved.layout().components().size());
      assertInstanceOf(WorkbenchChartConfig.class, saved.layout().components().get(1).config());

      String records = appTools.create_app_mock_records(appId, formName, """
        [
          {"name":"订单一","%s":100,"%s":"Alpha"},
          {"name":"订单二","%s":250,"%s":"Beta"}
        ]
        """.formatted(amountName, customerName, amountName, customerName), null, null, null);
      assertTrue(records.startsWith("["));

      WorkbenchComponentDataResponse metric = JsonUtils.parseToObject(
        appTools.get_app_workbench_component_data(appId, workbenchId, "metric", null, null, null),
        WorkbenchComponentDataResponse.class);
      assertInstanceOf(WorkbenchMetricData.class, metric.data());
      assertEquals(2, ((WorkbenchMetricData) metric.data()).value());

      WorkbenchComponentDataResponse chart = JsonUtils.parseToObject(
        appTools.get_app_workbench_component_data(appId, workbenchId, "chart", null, null, null),
        WorkbenchComponentDataResponse.class);
      assertInstanceOf(WorkbenchChartData.class, chart.data());
      assertEquals(2, ((WorkbenchChartData) chart.data()).rows().size());

      WorkbenchComponentDataResponse dataloader = JsonUtils.parseToObject(
        appTools.get_app_workbench_component_data(appId, workbenchId, "records", "1", "20", null),
        WorkbenchComponentDataResponse.class);
      assertInstanceOf(WorkbenchDataLoaderData.class, dataloader.data());
      assertEquals(2, ((WorkbenchDataLoaderData) dataloader.data()).total());

      assertTrue(appTools.get_app_workbench_component_data(
        appId, workbenchId, "tasks", null, null, null).startsWith("Error:"));

      AppNavResponse nav = JsonUtils.parseToObject(
        appTools.create_app_workbench_navigation(appId, "工作台", "Home", null),
        AppNavResponse.class);
      assertEquals("BUILTIN", nav.destination().type());
      assertEquals("workbench", nav.destination().id());
    } finally {
      deleteAppQuietly(appId);
    }
  }

  @Test
  void rejectsInvalidLayoutAndKeepsOriginalLayout() {
    String appId = "app_tools_layout_" + SUFFIX;
    String formName = "layout_form_" + SUFFIX;
    try {
      createAppAndForm(appId, formName);
      AppFormResponse original = JsonUtils.parseToObject(
        appTools.get_app_form(appId, formName), AppFormResponse.class);

      assertTrue(appTools.save_app_form_layout(appId, formName, """
        [{"type":"FIELD","name":"not_exists","width":"HALF"}]
        """).startsWith("Error: save_app_form_layout failed - "));
      assertTrue(appTools.save_app_form_layout(appId, formName, """
        [{"type":"FIELD","name":"name","width":"INVALID"}]
        """).startsWith("Error: save_app_form_layout failed - "));
      assertTrue(appTools.save_app_form_layout(appId, formName, """
        [
          {"type":"SECTION","label":"非法分栏","children":[]},
          {"type":"FIELD","name":"name"}
        ]
        """).startsWith("Error: save_app_form_layout failed - "));

      AppFormResponse current = JsonUtils.parseToObject(
        appTools.get_app_form(appId, formName), AppFormResponse.class);
      assertEquals(original.layout(), current.layout());
    } finally {
      deleteAppQuietly(appId);
    }
  }

  @Test
  void cleansUpFormWhenFieldCreationFails() {
    String appId = "app_tools_field_" + SUFFIX;
    String formName = "field_form_" + SUFFIX;
    try {
      createApp(appId);
      String result = appTools.create_app_form(
        appId, "字段失败表单", formName, "", false, """
          [{"label":"非法字段","type":"INVALID_TYPE","required":false}]
          """);

      assertTrue(result.startsWith("Error: create_app_form failed - "));
      AppFormResponse[] forms = JsonUtils.parseToObject(
        appTools.list_app_forms(appId), AppFormResponse[].class);
      assertEquals(0, forms.length);
    } finally {
      deleteAppQuietly(appId);
    }
  }

  @Test
  void createsAllCustomFieldsAndAcceptsStringOptions() {
    String appId = "app_tools_batch_" + SUFFIX;
    String formName = "batch_form_" + SUFFIX;
    try {
      createApp(appId);
      AppFormResponse form = JsonUtils.parseToObject(appTools.create_app_form(
        appId, "Categories", formName, "Category form", false, """
          [
            {"label":"Category name","type":"TEXT","required":true},
            {"label":"Gender","type":"SELECT","options":["Male","Female"]},
            {"label":"Description","type":"NTEXT"}
          ]
          """), AppFormResponse.class);

      assertEquals(formName, form.name());
      assertEquals(8, form.fields().size());
      assertEquals(3, form.layout().items().size());
      var genderField = fieldByName(form, "Gender");
      assertEquals(2, genderField.options().size());
      assertEquals("Male", genderField.options().getFirst().id());
      assertEquals("Male", genderField.options().getFirst().text());
      assertNotNull(fieldByName(form, "Category name").name());
      assertNotNull(fieldByName(form, "Description").name());
      AppFormResponse[] forms = JsonUtils.parseToObject(
        appTools.list_app_forms(appId), AppFormResponse[].class);
      assertEquals(1, forms.length);
      assertEquals(form.fields(), forms[0].fields());
      assertEquals(form.layout(), forms[0].layout());
    } finally {
      deleteAppQuietly(appId);
    }
  }

  @Test
  void createsReferenceFieldsToBuiltinForms() {
    String appId = "app_tools_builtin_ref_" + SUFFIX;
    String formName = "builtin_ref_form_" + SUFFIX;
    try {
      createApp(appId);
      AppFormResponse form = JsonUtils.parseToObject(appTools.create_app_form(
        appId, "Builtin references", formName, "", false, """
          [
            {"label":"Owner","name":"owner","type":"REFERENCE","referenceForm":"f_user"},
            {"label":"Role","name":"role","type":"REFERENCE","referenceForm":"f_role"},
            {"label":"Department","name":"department","type":"REFERENCE","referenceForm":"f_department"}
          ]
          """), AppFormResponse.class);

      var owner = fieldByName(form, "Owner");
      var role = fieldByName(form, "Role");
      var department = fieldByName(form, "Department");
      assertEquals("REFERENCE", owner.type());
      assertEquals("f_user", owner.referenceForm());
      assertEquals("REFERENCE", role.type());
      assertEquals("f_role", role.referenceForm());
      assertEquals("REFERENCE", department.type());
      assertEquals("f_department", department.referenceForm());

      var createdBy = form.fields().stream()
        .filter(field -> "created_by".equals(field.name()))
        .findFirst()
        .orElseThrow();
      var updatedBy = form.fields().stream()
        .filter(field -> "updated_by".equals(field.name()))
        .findFirst()
        .orElseThrow();
      assertEquals("REFERENCE", createdBy.type());
      assertEquals("f_user", createdBy.referenceForm());
      assertEquals("REFERENCE", updatedBy.type());
      assertEquals("f_user", updatedBy.referenceForm());
    } finally {
      deleteAppQuietly(appId);
    }
  }

  @Test
  void usesAiGeneratedFieldNamesAndRejectsInvalidNames() {
    String appId = "app_tools_names_" + SUFFIX;
    String formName = "name_form_" + SUFFIX;
    try {
      createApp(appId);
      AppFormResponse form = JsonUtils.parseToObject(appTools.create_app_form(
        appId, "订单", formName, "", false, """
          [
            {"label":"订单金额","name":"order_amount","type":"DECIMAL","required":true},
            {"label":"客户邮箱","name":"customer_email","type":"EMAIL","required":true}
          ]
          """), AppFormResponse.class);

      assertEquals("order_amount", fieldByName(form, "订单金额").name());
      assertEquals("customer_email", fieldByName(form, "客户邮箱").name());

      String result = appTools.create_app_form(
        appId, "非法字段", formName + "_invalid", "", false, """
          [{"label":"订单金额","name":"OrderAmount","type":"DECIMAL","required":true}]
          """);
      assertTrue(result.startsWith("Error: create_app_form failed - "));
    } finally {
      deleteAppQuietly(appId);
    }
  }

  @Test
  void stopsMockRecordCreationAndKeepsSuccessfulRecords() {
    String appId = "app_tools_records_" + SUFFIX;
    String formName = "record_form_" + SUFFIX;
    try {
      createAppAndForm(appId, formName);
      AppFormResponse form = JsonUtils.parseToObject(
        appTools.get_app_form(appId, formName), AppFormResponse.class);
      String amountName = fieldByName(form, "订单金额").name();
      String result = appTools.create_app_mock_records(appId, formName, """
        [
          {"name":"订单一","%s":100},
          {"name":"订单二"}
        ]
        """.formatted(amountName), null, null, null);

      assertTrue(result.startsWith(
        "Error: create_app_mock_records failed at record index 2 - "));
      var page = appRecordService.findRecords(
        appId, formName, 1, 20, null, null, null, null, null, null, null, null);
      assertEquals(1, page.list().size());
      assertNotNull(page.list().getFirst().get("id"));
    } finally {
      deleteAppQuietly(appId);
    }
  }

  private void createAppAndForm(String appId, String formName) {
    createApp(appId);
    String result = appTools.create_app_form(
      appId, "订单", formName, "", true, """
        [{"label":"订单金额","type":"DECIMAL","required":true}]
        """);
    assertTrue(result.startsWith("{"));
  }

  private void createApp(String appId) {
    AppResponse app = JsonUtils.parseToObject(appTools.create_app(
      "AppTools测试-" + SUFFIX, "", appId, "Boxes", "indigo"), AppResponse.class);
    assertEquals(appId, app.id());
  }

  private dev.flexmodel.apps.dto.AppFormFieldResponse fieldByName(AppFormResponse form, String label) {
    return form.fields().stream()
      .filter(field -> label.equals(field.label()))
      .findFirst()
      .orElseThrow();
  }

  private void deleteAppQuietly(String appId) {
    try {
      appService.deleteApp(appId);
    } catch (Exception ignored) {
      // 应用未创建或已被服务端清理时不阻断测试结果。
    }
  }
}
