package dev.flexmodel.mcp;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.SQLiteTestResource;
import dev.flexmodel.apps.AppFormService;
import dev.flexmodel.apps.AppRecordService;
import dev.flexmodel.apps.AppService;
import dev.flexmodel.apps.dto.AppFormResponse;
import dev.flexmodel.apps.dto.AppResponse;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
            {"label":"订单金额","type":"DECIMAL","required":true}
          ]
          """), AppFormResponse.class);
      assertEquals(formName, form.name());
      String amountName = fieldByName(form, "订单金额").name();
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

      var menu = JsonUtils.parseToObject(appTools.create_app_menu(
          appId, "ITEM", "订单", "ShoppingCart", null, "FORM", formName),
        dev.flexmodel.apps.dto.AppMenuResponse.class);
      assertEquals("FORM", menu.target().type());
      assertEquals(formName, menu.target().id());
      assertNotNull(menu.pageRoute());

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
        appId, formName, 1, 20, null, null, null, null, null, null);
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
