package dev.flexmodel.rest;

import dev.flexmodel.SQLiteTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItems;

@QuarkusTest
@QuarkusTestResource(SQLiteTestResource.class)
public class WorkbenchResourceTest {

  @Inject
  TestTokenHelper testTokenHelper;

  private static final String BASE_PATH = Resources.ROOT_PATH + "/apps/dev_test/workbenches";
  private static final String TEST_SUFFIX = Long.toHexString(System.nanoTime());

  @Test
  void testFindWorkbenchesUnauthorized() {
    given()
      .when()
      .get(BASE_PATH)
      .then()
      .statusCode(401);
  }

  @Test
  void testCapabilityCatalogAndDefaultWorkbench() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .get(BASE_PATH + "/capabilities")
      .then()
      .statusCode(200)
      .body("schemaVersion", greaterThanOrEqualTo(1))
      .body("components.type", hasItems("metric", "chart", "dataloader", "shortcuts", "tasks"))
      .body("availableValues.chartType", hasItems("line", "bar", "pie"));

    Response response = given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .get(BASE_PATH);
    response.then()
      .statusCode(200)
      .body("size()", greaterThanOrEqualTo(1));

    String defaultWorkbenchId = response.body().path("[0].id");
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .delete(BASE_PATH + "/" + defaultWorkbenchId)
      .then()
      .statusCode(400);
  }

  @Test
  void testCreatePatchAndDeleteWorkbench() {
    String workbenchId = createWorkbench();

    try {
      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .when()
        .get(BASE_PATH + "/" + workbenchId)
        .then()
        .statusCode(200)
        .body("id", equalTo(workbenchId))
        .body("layout.schemaVersion", equalTo(1))
        .body("layout.components[0].id", equalTo("component_tasks"))
        .body("layout.components[0].config.type", equalTo("tasks"))
        .body("layout.components[0].placement.x", equalTo(0))
        .body("layout.components[0].placement.y", equalTo(0))
        .body("layout.components[0].placement.w", equalTo(12))
        .body("layout.components[0].placement.h", equalTo(4));

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .contentType(ContentType.JSON)
        .body("{\"description\": \"局部更新描述-%s\"}".formatted(TEST_SUFFIX))
        .when()
        .patch(BASE_PATH + "/" + workbenchId)
        .then()
        .statusCode(200)
        .body("description", equalTo("局部更新描述-" + TEST_SUFFIX))
        .body("layout.components[0].placement.h", equalTo(4));

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .contentType(ContentType.JSON)
        .body("""
          {
            "layout": {
              "schemaVersion": 1,
              "components": [
                {
                  "id": "component_tasks",
                  "title": "我的任务",
                  "placement": {"x": 0, "y": 0, "w": 6, "h": 3},
                  "config": {"type": "tasks"}
                }
              ]
            }
          }
          """)
        .when()
        .patch(BASE_PATH + "/" + workbenchId)
        .then()
        .statusCode(200)
        .body("layout.components[0].placement.w", equalTo(6))
        .body("layout.components[0].placement.h", equalTo(3));
    } finally {
      deleteWorkbenchQuietly(workbenchId);
    }
  }

  @Test
  void testPatchValidations() {
    String workbenchId = createWorkbench();
    try {
      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .contentType(ContentType.JSON)
        .body("{\"name\": null}")
        .when()
        .patch(BASE_PATH + "/" + workbenchId)
        .then()
        .statusCode(400);

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .contentType(ContentType.JSON)
        .body("{\"layout\": null}")
        .when()
        .patch(BASE_PATH + "/" + workbenchId)
        .then()
        .statusCode(400);

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .contentType(ContentType.JSON)
        .body("""
          {
            "layout": {
              "schemaVersion": 1,
              "components": [
                {"id": "component_tasks", "title": "我的任务", "config": {"type": "tasks"}}
              ]
            }
          }
          """)
        .when()
        .patch(BASE_PATH + "/" + workbenchId)
        .then()
        .statusCode(400);
    } finally {
      deleteWorkbenchQuietly(workbenchId);
    }
  }

  @Test
  void testSecondWorkbenchCanBeDeleted() {
    String workbenchId = createWorkbench();

    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .delete(BASE_PATH + "/" + workbenchId)
      .then()
      .statusCode(204);

    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .get(BASE_PATH + "/" + workbenchId)
      .then()
      .statusCode(404);
  }

  private String createWorkbench() {
    return given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body("""
        {
          "name": "销售工作台-%s-%s",
          "description": "工作台配置测试",
          "layout": {
            "schemaVersion": 1,
            "components": [
              {
                "id": "component_tasks",
                "title": "我的任务",
                "placement": {"x": 0, "y": 0, "w": 12, "h": 4},
                "config": {"type": "tasks"}
              }
            ]
          }
        }
        """.formatted(TEST_SUFFIX, UUID.randomUUID()))
      .when()
      .post(BASE_PATH)
      .then()
      .statusCode(201)
      .extract()
      .path("id");
  }

  private void deleteWorkbenchQuietly(String workbenchId) {
    if (workbenchId == null) return;
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .delete(BASE_PATH + "/" + workbenchId);
  }
}
