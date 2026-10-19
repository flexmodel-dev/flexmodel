package dev.flexmodel.rest;

import dev.flexmodel.SQLiteTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@QuarkusTestResource(SQLiteTestResource.class)
public class SystemFormResourceTest {

  private static final String BASE_PATH = Resources.ROOT_PATH + "/system/forms";

  @Inject
  TestTokenHelper testTokenHelper;

  @Test
  void testFindFormsUnauthorized() {
    given()
      .when()
      .get(BASE_PATH)
      .then()
      .statusCode(401);
  }

  @Test
  void testOnlyThreeSystemFormsAreExposed() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .get(BASE_PATH)
      .then()
      .statusCode(200)
      .body("name", containsInRelativeOrder("f_user", "f_department", "f_role"))
      .body("name", hasSize(3))
      .body("name", not(hasItem("f_user_department")))
      .body("name", not(hasItem("f_user_role")))
      .body("name", not(hasItem("f_role_resource")));
  }

  @Test
  void testRoleFormContainsServerInjectedPermissionTree() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .get(BASE_PATH + "/f_role")
      .then()
      .statusCode(200)
      .body("name", equalTo("f_role"))
      .body("designable", equalTo(true))
      .body("resources.id", hasItem(1))
      .body("fields.find { it.name == 'resourceIds' }.type", equalTo("MULTIREFERENCE"))
      .body("layout.items.find { it.name == 'resourceIds' }.type", equalTo("FIELD"));
  }

  @Test
  void testUserFormUsesMultiReferenceAndSubform() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .get(BASE_PATH + "/f_user")
      .then()
      .statusCode(200)
      .body("fields.find { it.name == 'roleIds' }.type", equalTo("MULTIREFERENCE"))
      .body("fields.find { it.name == 'roleIds' }.referenceForm", equalTo("f_role"))
      .body("layout.items.find { it.type == 'SUBFORM' }.name", equalTo("departments"))
      .body("layout.items.find { it.type == 'SUBFORM' }.formName", equalTo("f_user_department"))
      .body("layout.items.find { it.type == 'SUBFORM' }.referenceField", equalTo("user_id"));
  }

  @Test
  void testSaveUserFormAddsAndRemovesExtensionField() {
    Response response = given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .get(BASE_PATH + "/f_user")
      .then()
      .statusCode(200)
      .extract()
      .response();
    String original = response.asString();
    JsonObject payload = new JsonObject(original);
    JsonArray fields = payload.getJsonArray("fields");
    fields.add(new JsonObject()
      .put("name", "extension_test_field")
      .put("label", "扩展测试")
      .put("type", "TEXT")
      .put("required", false)
      .put("locked", false)
      .put("extension", true)
      .put("virtual", false)
      .put("readonly", false));

    try {
      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .contentType("application/json")
        .body(payload.encode())
        .when()
        .put(BASE_PATH + "/f_user")
        .then()
        .statusCode(200)
        .body("fields.findAll { it.name == 'extension_test_field' }.extension", hasItem(true));
    } finally {
      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .contentType("application/json")
        .body(original)
        .when()
        .put(BASE_PATH + "/f_user")
        .then()
        .statusCode(200);
    }
  }
}
