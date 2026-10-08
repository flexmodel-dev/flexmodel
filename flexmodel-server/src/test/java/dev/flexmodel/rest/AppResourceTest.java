package dev.flexmodel.rest;

import dev.flexmodel.SQLiteTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * AppResource 集成测试。
 *
 * @author cjbi
 */
@QuarkusTest
@QuarkusTestResource(SQLiteTestResource.class)
public class AppResourceTest {

  @Inject
  TestTokenHelper testTokenHelper;

  private static final String BASE_PATH = Resources.ROOT_PATH + "/apps";

  @Test
  void testFindApps() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .get(BASE_PATH)
      .then()
      .statusCode(200)
      .body("size()", greaterThanOrEqualTo(1))
      .body("id", hasItem("dev_test"))
      .body("projectId", hasItem("dev_test"));
  }

  @Test
  void testFindAppDetail() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .get(BASE_PATH + "/dev_test")
      .then()
      .statusCode(200)
      .body("id", equalTo("dev_test"))
      .body("projectId", equalTo("dev_test"))
      .body("name", notNullValue())
      .body("iconName", equalTo("Boxes"))
      .body("primaryColor", equalTo("indigo"))
      .body("enabled", equalTo(true));
  }

  @Test
  void testCreateAppWithGeneratedProjectId() {
    String createdId = given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body("""
        {
          "name": "应用管理E2E",
          "description": "应用管理集成测试",
          "iconName": "Folder",
          "primaryColor": "green"
        }
        """)
      .when()
      .post(BASE_PATH)
      .then()
      .statusCode(201)
      .body("id", notNullValue())
      .body("projectId", notNullValue())
      .body("name", equalTo("应用管理E2E"))
      .body("iconName", equalTo("Folder"))
      .body("primaryColor", equalTo("green"))
      .extract()
      .path("id");

    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .delete(BASE_PATH + "/" + createdId)
      .then()
      .statusCode(204);
  }

  @Test
  void testCreateAppWithExplicitProjectId() {
    String projectId = "app_e2e_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body("""
        {
          "name": "显式ID应用",
          "description": "显式项目ID创建",
          "projectId": "%s",
          "iconName": "Tags",
          "primaryColor": "blue"
        }
        """.formatted(projectId))
      .when()
      .post(BASE_PATH)
      .then()
      .statusCode(201)
      .body("id", equalTo(projectId))
      .body("projectId", equalTo(projectId));

    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .delete(BASE_PATH + "/" + projectId)
      .then()
      .statusCode(204);

    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .get(BASE_PATH + "/" + projectId)
      .then()
      .statusCode(404);
  }

  @Test
  void testUpdateAppPreservesOtherMetadata() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body("""
        {
          "id": "dev_test",
          "name": "开发测试",
          "description": "应用管理测试项目",
          "metadata": {
            "logSettings": {
              "logRetentionDays": 30
            },
            "app": {
              "iconName": "Tags",
              "primaryColor": "blue"
            }
          }
        }
        """)
      .when()
      .put("/projects/dev_test")
      .then()
      .statusCode(200);

    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body("""
        {
          "name": "应用管理更新",
          "description": "更新后的应用描述",
          "iconName": "Folder",
          "primaryColor": "green"
        }
        """)
      .when()
      .put(BASE_PATH + "/dev_test")
      .then()
      .statusCode(200)
      .body("id", equalTo("dev_test"))
      .body("name", equalTo("应用管理更新"))
      .body("description", equalTo("更新后的应用描述"))
      .body("iconName", equalTo("Folder"))
      .body("primaryColor", equalTo("green"));

    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .get("/projects/dev_test")
      .then()
      .statusCode(200)
      .body("metadata.logSettings.logRetentionDays", equalTo(30))
      .body("metadata.app.iconName", equalTo("Folder"))
      .body("metadata.app.primaryColor", equalTo("green"));
  }

  @Test
  void testDeleteDefaultProjectReturns404() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .delete(BASE_PATH + "/default")
      .then()
      .statusCode(404);
  }

  @Test
  void testCreateAppWithInvalidProjectIdReturns400() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body("""
        {
          "name": "非法项目ID应用",
          "projectId": "1-invalid-id",
          "iconName": "Boxes",
          "primaryColor": "indigo"
        }
        """)
      .when()
      .post(BASE_PATH)
      .then()
      .statusCode(400);
  }

  @Test
  void testCreateAppWithInvalidIconReturns400() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body("""
        {
          "name": "非法图标应用",
          "iconName": "NotExists",
          "primaryColor": "indigo"
        }
        """)
      .when()
      .post(BASE_PATH)
      .then()
      .statusCode(400);
  }

  @Test
  void testCreateAppWithBlankNameReturns400() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body("""
        {
          "name": " ",
          "iconName": "Boxes",
          "primaryColor": "indigo"
        }
        """)
      .when()
      .post(BASE_PATH)
      .then()
      .statusCode(400);
  }

  @Test
  void testFindAppsUnauthorized() {
    given()
      .when()
      .get(BASE_PATH)
      .then()
      .statusCode(401);
  }
}
