package dev.flexmodel.rest;

import dev.flexmodel.SQLiteTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * WorkbenchResource 集成测试。
 *
 * @author cjbi
 */
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
  void testListCreatesDefaultWorkbenchAndProtectsLastWorkbench() {
    Response response = given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .get(BASE_PATH);
    response.then()
      .statusCode(200)
      .body("size()", greaterThanOrEqualTo(1))
      .body("name", hasItem("默认工作台"));

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
        .body("name", equalTo("销售工作台-" + TEST_SUFFIX))
        .body("layout.components[0].id", equalTo("component_tasks"))
        .body("layout.items[0].componentId", equalTo("component_tasks"))
        .body("layout.items[0].x", equalTo(0))
        .body("layout.items[0].y", equalTo(0))
        .body("layout.items[0].w", equalTo(12))
        .body("layout.items[0].h", equalTo(4));

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .contentType(ContentType.JSON)
        .body("""
          {"description": "局部更新描述-%s"}
          """.formatted(TEST_SUFFIX))
        .when()
        .patch(BASE_PATH + "/" + workbenchId)
        .then()
        .statusCode(200)
        .body("name", equalTo("销售工作台-" + TEST_SUFFIX))
        .body("description", equalTo("局部更新描述-" + TEST_SUFFIX))
        .body("layout.items[0].h", equalTo(4));

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .contentType(ContentType.JSON)
        .body("""
          {
            "layout": {
              "components": [
                {
                  "id": "component_tasks",
                  "title": "我的任务",
                  "type": "tasks",
                  "config": {}
                }
              ],
              "items": [
                {
                  "componentId": "component_tasks",
                  "x": 0,
                  "y": 0,
                  "w": 6,
                  "h": 3
                }
              ]
            }
          }
          """)
        .when()
        .patch(BASE_PATH + "/" + workbenchId)
        .then()
        .statusCode(200)
        .body("layout.items[0].componentId", equalTo("component_tasks"))
        .body("layout.items[0].x", equalTo(0))
        .body("layout.items[0].y", equalTo(0))
        .body("layout.items[0].w", equalTo(6))
        .body("layout.items[0].h", equalTo(3));

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .contentType(ContentType.JSON)
        .body("""
          {
            "layout": {
              "components": [
                {
                  "id": "component_tasks",
                  "title": "????",
                  "type": "tasks",
                  "config": {}
                }
              ],
              "items": [
                {
                  "componentId": "component_tasks",
                  "x": 2,
                  "y": 3,
                  "w": 6,
                  "h": 4
                }
              ]
            }
          }
          """)
        .when()
        .patch(BASE_PATH + "/" + workbenchId)
        .then()
        .statusCode(200)
        .body("layout.items[0].x", equalTo(2))
        .body("layout.items[0].y", equalTo(3))
        .body("layout.items[0].w", equalTo(6))
        .body("layout.items[0].h", equalTo(4));
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
              "components": [{"id": "component_tasks", "title": "我的任务", "type": "tasks"}],
              "items": [{"componentId": "not_exists", "x": 0, "y": 0, "w": 12, "h": 3}]
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
          "name": "销售工作台-%s",
          "description": "工作台配置测试",
          "layout": {
            "components": [
              {
                "id": "component_tasks",
                "title": "我的任务",
                "type": "tasks",
                "config": {}
              }
            ],
            "items": [
              {
                "componentId": "component_tasks",
                "x": 0,
                "y": 0,
                "w": 12,
                "h": 4
              }
            ]
          }
        }
        """.formatted(TEST_SUFFIX))
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
