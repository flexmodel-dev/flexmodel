package dev.flexmodel.rest;

import dev.flexmodel.SQLiteTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

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
        "label": "名称字段表单-%s",
        "name": "name_form_%s",
        "description": "创建名称字段",
        "createNameField": true
      }
      """.formatted(TEST_SUFFIX, TEST_SUFFIX), 201);
    String formName = response.body().path("name");

    try {
      response.then()
        .body("name", equalTo("name_form_" + TEST_SUFFIX))
        .body("label", equalTo("名称字段表单-" + TEST_SUFFIX))
        .body("displayField", equalTo("name"))
        .body("searchField", equalTo("name"))
        .body("managed", equalTo(true))
        .body("fields.name", hasItems("id", "created_by", "updated_by", "created_at", "updated_at", "name"))
        .body("fields.find { it.name == 'id' }.builtin", equalTo(true))
        .body("fields.find { it.name == 'name' }.required", equalTo(true));

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
        .body("forms.label", hasItem("名称字段表单-" + TEST_SUFFIX));
    } finally {
      deleteFormQuietly(formName);
    }
  }

  @Test
  void testCreateFormWithoutDisplayField() {
    Response response = createForm("""
      {
        "label": "无名称字段表单-%s",
        "name": "plain_form_%s",
        "createNameField": false
      }
      """.formatted(TEST_SUFFIX, TEST_SUFFIX), 201);
    String formName = response.body().path("name");

    try {
      response.then()
        .body("name", equalTo("plain_form_" + TEST_SUFFIX))
        .body("label", equalTo("无名称字段表单-" + TEST_SUFFIX))
        .body("displayField", nullValue())
        .body("searchField", nullValue())
        .body("fields.name", not(hasItem("name")))
        .body("layout.fields", not(hasItem("name")));
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
        "createNameField": true
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
      reference.then()
        .body("type", equalTo("REFERENCE"))
        .body("referenceForm", equalTo(formName));

      Response fields = given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .when()
        .get(BASE_PATH + "/" + formName);
      fields.then()
        .statusCode(200)
        .body("fields.label", hasItems("客户名称", "金额", "选项", "关联表单"))
        .body("fields.find { it.label == '客户名称' }.required", equalTo(true))
        .body("fields.find { it.label == '金额' }.type", equalTo("DECIMAL"))
        .body("fields.find { it.label == '选项' }.options.size()", equalTo(2));

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
            "fields": ["name"],
            "widths": {"name": 50},
            "properties": {
              "name": {"label": "表单名称", "tip": "请输入名称", "required": true, "readonly": false, "hidden": false}
            },
            "advanced": {"reserved": true}
          }
          """)
        .when()
        .put(BASE_PATH + "/" + formName + "/layout")
        .then()
        .statusCode(200)
        .body("layout.fields", contains("name"))
        .body("layout.widths.name", equalTo(50))
        .body("layout.properties.name.label", equalTo("表单名称"))
        .body("layout.advanced.reserved", equalTo(true));

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .when()
        .get(BASE_PATH + "/" + formName)
        .then()
        .statusCode(200)
        .body("layout.fields", contains("name"));
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
        .body("layout.fields", not(hasItem("name")));
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
        "createNameField": false
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
