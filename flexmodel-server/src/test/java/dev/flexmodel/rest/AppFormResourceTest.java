package dev.flexmodel.rest;

import dev.flexmodel.SQLiteTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * AppFormResource 集成测试。
 *
 * @author cjbi
 */
@QuarkusTest
@QuarkusTestResource(SQLiteTestResource.class)
public class AppFormResourceTest {

  @Inject
  TestTokenHelper testTokenHelper;

  private static final String BASE_PATH = Resources.ROOT_PATH + "/apps/dev_test/forms";
  private static final String MENU_PATH = Resources.ROOT_PATH + "/apps/dev_test/menus";
  private static final String TEST_SUFFIX = Long.toHexString(System.nanoTime());

  @Test
  void testFindFormsUnauthorized() {
    given()
      .when()
      .get(BASE_PATH)
      .then()
      .statusCode(401);
  }

  @Test
  void testCreateFormWithDisplayField() {
    Response response = createForm("""
      {
        "label": "主显字段表单-%s",
        "name": "name_form_%s",
        "description": "创建主显字段",
        "createDisplayField": true
      }
      """.formatted(TEST_SUFFIX, TEST_SUFFIX), 201);
    String formName = response.body().path("name");

    try {
      response.then()
        .body("name", equalTo("name_form_" + TEST_SUFFIX))
        .body("label", equalTo("主显字段表单-" + TEST_SUFFIX))
        .body("displayField", equalTo("name"))
        .body("searchField", equalTo("name"))
        .body("managed", equalTo(true))
        .body("fields.name", hasItems("id", "created_by", "updated_by", "created_at", "updated_at", "name"))
        .body("fields.find { it.name == 'id' }.builtin", equalTo(true))
        .body("fields.find { it.name == 'name' }.required", equalTo(true))
        .body("layout.items", empty());

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .when()
        .get(BASE_PATH)
        .then()
        .statusCode(200)
        .body("name", hasItem(formName));

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .when()
        .get(MENU_PATH + "/targets")
        .then()
        .statusCode(200)
        .body("forms.id", hasItem(formName))
        .body("forms.label", hasItem("主显字段表单-" + TEST_SUFFIX));
    } finally {
      deleteFormQuietly(formName);
    }
  }

  @Test
  void testCreateFormWithoutDisplayField() {
    Response response = createForm("""
      {
        "label": "无主显字段表单-%s",
        "name": "plain_form_%s",
        "createDisplayField": false
      }
      """.formatted(TEST_SUFFIX, TEST_SUFFIX), 201);
    String formName = response.body().path("name");

    try {
      response.then()
        .body("name", equalTo("plain_form_" + TEST_SUFFIX))
        .body("label", equalTo("无主显字段表单-" + TEST_SUFFIX))
        .body("displayField", nullValue())
        .body("searchField", nullValue())
        .body("fields.name", not(hasItem("name")))
        .body("layout.items", empty());
    } finally {
      deleteFormQuietly(formName);
    }
  }

  @Test
  void testCreateFieldDoesNotAutomaticallyAddLayout() {
    Response form = createForm("""
      {
        "label": "字段不自动布局表单-%s",
        "name": "field_no_layout_form_%s",
        "createDisplayField": false
      }
      """.formatted(TEST_SUFFIX, TEST_SUFFIX), 201);
    String formName = form.body().path("name");

    try {
      Response field = createField(formName, """
        {
          "label": "客户名称",
          "type": "TEXT",
          "required": true
        }
        """, 201);
      String fieldName = field.body().path("name");

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .when()
        .get(BASE_PATH + "/" + formName)
        .then()
        .statusCode(200)
        .body("fields.name", hasItem(fieldName))
        .body("layout.items", empty());

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .contentType(ContentType.JSON)
        .body("""
          {"items": [{"type": "FIELD", "name": "id", "width": "HALF"}]}
          """)
        .when()
        .put(BASE_PATH + "/" + formName + "/layout")
        .then()
        .statusCode(200)
        .body("layout.items[0].name", equalTo("id"));
    } finally {
      deleteFormQuietly(formName);
    }
  }

  @Test
  void testPatchViewTabsAndLayoutPreservation() {
    Response form = createForm("""
      {
        "label": "页签表单-%s",
        "name": "view_tab_form_%s",
        "description": "视图页签测试",
        "createDisplayField": true
      }
      """.formatted(TEST_SUFFIX, TEST_SUFFIX), 201);
    String formName = form.body().path("name");

    try {
      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .contentType(ContentType.JSON)
        .body("""
          {
            "viewTabs": [
              {
                "id": "zhang",
                "label": "张姓",
                "filter": {
                  "_and": [
                    {"name": {"_contains": "张"}}
                  ]
                }
              }
            ]
          }
          """)
        .when()
        .patch(BASE_PATH + "/" + formName)
        .then()
        .statusCode(200)
        .body("label", equalTo("页签表单-" + TEST_SUFFIX))
        .body("description", equalTo("视图页签测试"))
        .body("viewTabs[0].id", equalTo("zhang"))
        .body("viewTabs[0].label", equalTo("张姓"))
        .body("viewTabs[0].filter._and[0].name._contains", equalTo("张"));

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .contentType(ContentType.JSON)
        .body("""
          {
            "items": [
              {"type": "FIELD", "name": "name", "width": "HALF"}
            ]
          }
          """)
        .when()
        .put(BASE_PATH + "/" + formName + "/layout")
        .then()
        .statusCode(200)
        .body("viewTabs[0].id", equalTo("zhang"));

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .when()
        .get(BASE_PATH + "/" + formName)
        .then()
        .statusCode(200)
        .body("viewTabs[0].label", equalTo("张姓"));

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .contentType(ContentType.JSON)
        .body("""
          {"viewTabs": [{"id": "zhang", "label": "张姓", "filter": {"_or": []}}]}
          """)
        .when()
        .patch(BASE_PATH + "/" + formName)
        .then()
        .statusCode(400);
    } finally {
      deleteFormQuietly(formName);
    }
  }

  @Test
  void testFormFieldCrudAndTypeMappings() {
    Response form = createForm("""
      {
        "label": "字段映射表单-%s",
        "name": "field_form_%s",
        "createDisplayField": true
      }
      """.formatted(TEST_SUFFIX, TEST_SUFFIX), 201);
    String formName = form.body().path("name");

    try {
      createField(formName, """
        {
          "label": "客户名称",
          "type": "TEXT",
          "required": true
        }
        """, 201);
      createField(formName, """
        {
          "label": "金额",
          "type": "DECIMAL",
          "required": false
        }
        """, 201);
      createField(formName, """
        {
          "label": "选项",
          "type": "PICKLIST",
          "options": [
            {"id": "1", "text": "选项一"},
            {"id": "2", "text": "选项二"}
          ]
        }
        """, 201);
      Response reference = createField(formName, """
        {
          "label": "关联表单",
          "type": "REFERENCE",
          "referenceForm": "%s"
        }
        """.formatted(formName), 201);
      String referenceField = reference.body().path("name");
      Response fields = given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .when()
        .get(BASE_PATH + "/" + formName);
      fields.then()
        .statusCode(200)
        .body("fields.label", hasItems("客户名称", "金额", "选项", "关联表单"))
        .body("fields.find { it.label == '客户名称' }.required", equalTo(true))
        .body("fields.find { it.label == '金额' }.type", equalTo("DECIMAL"))
        .body("fields.find { it.label == '选项' }.options.size()", equalTo(2))
        .body("fields.find { it.label == '关联表单' }.referenceForm", equalTo(formName));
      String customerField = fields.body().path("fields.find { it.label == '客户名称' }.name");
      String amountField = fields.body().path("fields.find { it.label == '金额' }.name");
      String optionField = fields.body().path("fields.find { it.label == '选项' }.name");
      reference.then()
        .body("type", equalTo("REFERENCE"))
        .body("referenceForm", equalTo(formName));

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .when()
        .get(BASE_PATH)
        .then()
        .statusCode(200)
        .body("find { it.name == '%s' }.fields.find { it.label == '关联表单' }.referenceForm".formatted(formName),
          equalTo(formName));

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .contentType(ContentType.JSON)
        .body("""
          {
            "label": "客户名称更新",
            "required": false
          }
          """)
        .when()
        .put(BASE_PATH + "/" + formName + "/fields/" + fields.body().path("fields.find { it.label == '客户名称' }.name"))
        .then()
        .statusCode(200)
        .body("label", equalTo("客户名称更新"))
        .body("required", equalTo(false));

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .when()
        .delete(BASE_PATH + "/" + formName + "/fields/" + referenceField)
        .then()
        .statusCode(204);

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .when()
        .get(BASE_PATH + "/" + formName)
        .then()
        .statusCode(200)
        .body("fields.name", not(hasItem(referenceField)));

      createField(formName, """
        {
          "label": "非法类型",
          "type": "NOT_EXISTS"
        }
        """, 400);
      createField(formName, """
        {
          "label": "非法引用",
          "type": "REFERENCE",
          "referenceForm": "not_exists"
        }
        """, 400);

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .when()
        .delete(BASE_PATH + "/" + formName + "/fields/id")
        .then()
        .statusCode(400);

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .contentType(ContentType.JSON)
        .body("""
          {
            "items": [
              {"type": "SECTION", "label": "基础信息", "width": "FULL"},
              {"type": "FIELD", "name": "name", "label": "表单名称", "tip": "请输入名称", "width": "HALF"},
              {"type": "FIELD", "name": "%s", "width": "HALF"},
              {"type": "FIELD", "name": "%s", "width": "THIRD"},
              {"type": "FIELD", "name": "%s", "width": "QUARTER"}
            ]
          }
          """.formatted(customerField, amountField, optionField))
        .when()
        .put(BASE_PATH + "/" + formName + "/layout")
        .then()
        .statusCode(200)
        .body("layout.items[0].type", equalTo("SECTION"))
        .body("layout.items[0].label", equalTo("基础信息"))
        .body("layout.items[1].type", equalTo("FIELD"))
        .body("layout.items[1].name", equalTo("name"))
        .body("layout.items[1].label", equalTo("表单名称"))
        .body("layout.items[1].width", equalTo("HALF"));

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .when()
        .get(BASE_PATH + "/" + formName)
        .then()
        .statusCode(200)
        .body("layout.items.name", hasItem("name"));
      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .contentType(ContentType.JSON)
        .body("""
          {
            "label": "表单名称",
            "required": false
          }
          """)
        .when()
        .put(BASE_PATH + "/" + formName + "/fields/name")
        .then()
        .statusCode(200)
        .body("label", equalTo("表单名称"))
        .body("required", equalTo(false));

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .when()
        .delete(BASE_PATH + "/" + formName + "/fields/name")
        .then()
        .statusCode(204);

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .when()
        .get(BASE_PATH + "/" + formName)
        .then()
        .statusCode(200)
        .body("displayField", nullValue())
        .body("searchField", nullValue())
        .body("fields.name", not(hasItem("name")))
        .body("layout.items.name", not(hasItem("name")));
    } finally {
      deleteFormQuietly(formName);
    }
  }

  @Test
  void testUpdateFormAndDeleteBoundForm() {
    Response form = createForm("""
      {
        "label": "更新表单-%s",
        "name": "update_form_%s",
        "createDisplayField": false
      }
      """.formatted(TEST_SUFFIX, TEST_SUFFIX), 201);
    String formName = form.body().path("name");
    String menuId = null;

    try {
      Response updatedForm = given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .contentType(ContentType.JSON)
        .body("""
          {
            "label": "更新后表单-%s",
            "description": "更新后的表单"
          }
          """.formatted(TEST_SUFFIX))
        .when()
        .put(BASE_PATH + "/" + formName)
        .then()
        .statusCode(200)
        .body("name", equalTo(formName))
        .body("label", equalTo("更新后表单-" + TEST_SUFFIX))
        .extract()
        .response();
      Response displayField = createField(formName, """
        {
          "label": "主显字段",
          "type": "TEXT",
          "required": false
        }
        """, 201);
      String displayFieldName = displayField.body().path("name");

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .contentType(ContentType.JSON)
        .body("""
          {
            "label": "更新后表单-%s",
            "description": "更新后的表单",
            "displayField": "%s"
          }
          """.formatted(TEST_SUFFIX, displayFieldName))
        .when()
        .put(BASE_PATH + "/" + formName)
        .then()
        .statusCode(200)
        .body("displayField", equalTo(displayFieldName))
        .body("searchField", equalTo(displayFieldName));

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .contentType(ContentType.JSON)
        .body("""
          {
            "label": "更新后表单-%s",
            "description": "更新后的表单",
            "displayField": "id"
          }
          """.formatted(TEST_SUFFIX))
        .when()
        .put(BASE_PATH + "/" + formName)
        .then()
        .statusCode(400);

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .when()
        .get(MENU_PATH + "/targets")
        .then()
        .statusCode(200)
        .body("forms.label", hasItem("更新后表单-" + TEST_SUFFIX));

      menuId = createMenu("""
        {
          "type": "ITEM",
          "name": "表单菜单-%s",
          "target": {"type": "FORM", "id": "%s"}
        }
        """.formatted(TEST_SUFFIX, formName), 201);

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .when()
        .delete(BASE_PATH + "/" + formName)
        .then()
        .statusCode(400);

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .when()
        .delete(MENU_PATH + "/" + menuId)
        .then()
        .statusCode(204);
      menuId = null;

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .when()
        .delete(BASE_PATH + "/" + formName)
        .then()
        .statusCode(204);

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .when()
        .get(BASE_PATH + "/" + formName)
        .then()
        .statusCode(404);
    } finally {
      deleteMenuQuietly(menuId);
      deleteFormQuietly(formName);
    }
  }

  @Test
  void testMissingFormReturns404() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .get(BASE_PATH + "/not_exists")
      .then()
      .statusCode(404);

    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .delete(BASE_PATH + "/not_exists")
      .then()
      .statusCode(404);
  }

  @Test
  void testFormLayoutValidation() {
    String formName = "layout_validation_" + TEST_SUFFIX;
    createForm("""
      {
        "label": "布局校验表单",
        "name": "%s",
        "createDisplayField": true
      }
      """.formatted(formName), 201);

    try {
      saveLayout(formName, "{\"items\": []}", 400);
      saveLayout(formName, "{\"items\": [{\"type\": \"UNKNOWN\", \"label\": \"未知\"}]}", 400);
      saveLayout(formName, "{\"items\": [{\"type\": \"FIELD\", \"name\": \"name\", \"width\": \"50\"}]}", 400);
      saveLayout(formName, "{\"items\": [{\"type\": \"FIELD\"}]}", 400);
      saveLayout(formName, "{\"items\": [{\"type\": \"SUBFORM\", \"referenceField\": \"name\"}]}", 400);
    } finally {
      deleteFormQuietly(formName);
    }
  }

  @Test
  void testFormLayoutFieldReference() {
    String formName = "layout_field_" + TEST_SUFFIX;
    createForm("""
      {
        "label": "布局字段校验表单",
        "name": "%s",
        "createDisplayField": true
      }
      """.formatted(formName), 201);

    try {
      saveLayout(formName, "{\"items\": [{\"type\": \"FIELD\", \"name\": \"not_exists\"}]}", 400);
      saveLayout(formName, """
        {"items": [
          {"type": "FIELD", "name": "name"},
          {"type": "FIELD", "name": "name"}
        ]}
        """, 400);
      saveLayout(formName, "{\"items\": [{\"type\": \"SECTION\", \"label\": \"基础信息\"}]}", 400);
    } finally {
      deleteFormQuietly(formName);
    }
  }

  @Test
  void testFormLayoutSubform() {
    String mainName = "subform_main_" + TEST_SUFFIX;
    String childName = "subform_child_" + TEST_SUFFIX;
    createForm("""
      {
        "label": "主表单-%s",
        "name": "%s",
        "description": "子表单主表单",
        "createDisplayField": true
      }
      """.formatted(TEST_SUFFIX, mainName), 201);
    createForm("""
      {
        "label": "子表单-%s",
        "name": "%s",
        "description": "子表单子表",
        "createDisplayField": true
      }
      """.formatted(TEST_SUFFIX, childName), 201);
    Response reference = createField(childName, """
      {
        "label": "主表单引用",
        "type": "TEXT",
        "required": false
      }
      """, 201);
    String referenceField = reference.body().path("name");

    try {
      saveLayout(mainName, """
        {"items": [
          {"type": "FIELD", "name": "name"},
          {"type": "SUBFORM", "formName": "%s", "referenceField": "%s"},
          {"type": "SUBFORM", "formName": "%s", "referenceField": "%s"}
        ]}
        """.formatted(childName, referenceField, childName, referenceField), 400);

      Response layoutResponse = saveLayout(mainName, """
        {"items": [
          {"type": "SECTION", "label": "基础信息", "width": "FULL"},
          {"type": "FIELD", "name": "name", "width": "HALF"},
          {"type": "SUBFORM", "label": "子表记录", "formName": "%s", "referenceField": "%s", "width": "FULL"}
        ]}
        """.formatted(childName, referenceField), 200);
      layoutResponse.then()
        .body("layout.items.type", contains("SECTION", "FIELD", "SUBFORM"))
        .body("layout.items.find { it.type == 'SUBFORM' }.formName", equalTo(childName))
        .body("layout.items.find { it.type == 'SUBFORM' }.referenceField", equalTo(referenceField));

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .when()
        .delete(BASE_PATH + "/" + childName)
        .then()
        .statusCode(400);

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .when()
        .delete(BASE_PATH + "/" + childName + "/fields/" + referenceField)
        .then()
        .statusCode(400);

      saveLayout(mainName, "{\"items\": [{\"type\": \"FIELD\", \"name\": \"name\"}]}", 200)
        .then()
        .body("layout.items.type", contains("FIELD"));

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .when()
        .delete(BASE_PATH + "/" + childName + "/fields/" + referenceField)
        .then()
        .statusCode(204);
    } finally {
      deleteFormQuietly(childName);
      deleteFormQuietly(mainName);
    }
  }

  @Test
  void testCreateFormBindsExistingModel() {
    String formName = "bind_model_" + TEST_SUFFIX;
    createForm("""
      {
        "label": "绑定实体表单",
        "name": "%s",
        "createDisplayField": true
      }
      """.formatted(formName), 201);

    try {
      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .contentType(ContentType.JSON)
        .body("{\"name\": \"绑定记录\"}")
        .when()
        .post(BASE_PATH + "/" + formName + "/records")
        .then()
        .statusCode(201)
        .body("name", equalTo("绑定记录"));
    } finally {
      deleteFormQuietly(formName);
    }
  }

  @Test
  void testCreateFormDuplicateRecord() {
    String formName = "duplicate_form_" + TEST_SUFFIX;
    createForm("""
      {
        "label": "重复表单",
        "name": "%s",
        "createDisplayField": true
      }
      """.formatted(formName), 201);

    try {
      createForm("""
        {
          "label": "重复表单",
          "name": "%s",
          "createDisplayField": true
        }
        """.formatted(formName), 400);
    } finally {
      deleteFormQuietly(formName);
    }
  }

  private Response createForm(String body, int expectedStatus) {
    Response response = given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body(body)
      .when()
      .post(BASE_PATH);
    response.then().statusCode(expectedStatus);
    return response;
  }

  private Response createField(String formName, String body, int expectedStatus) {
    Response response = given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body(body)
      .when()
      .post(BASE_PATH + "/" + formName + "/fields");
    response.then().statusCode(expectedStatus);
    return response;
  }

  private Response saveLayout(String formName, String body, int expectedStatus) {
    Response response = given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body(body)
      .when()
      .put(BASE_PATH + "/" + formName + "/layout");
    response.then().statusCode(expectedStatus);
    return response;
  }

  private String createMenu(String body, int expectedStatus) {
    Response response = given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body(body)
      .when()
      .post(MENU_PATH);
    response.then().statusCode(expectedStatus);
    return expectedStatus == 201 ? response.body().path("id") : null;
  }

  private void deleteFormQuietly(String formName) {
    if (formName == null) return;
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .delete(BASE_PATH + "/" + formName);
  }

  private void deleteMenuQuietly(String menuId) {
    if (menuId == null) return;
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .delete(MENU_PATH + "/" + menuId);
  }
}
