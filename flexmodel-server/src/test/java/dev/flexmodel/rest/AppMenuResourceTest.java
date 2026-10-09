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
 * AppMenuResource 集成测试。
 *
 * @author cjbi
 */
@QuarkusTest
@QuarkusTestResource(SQLiteTestResource.class)
public class AppMenuResourceTest {

  @Inject
  TestTokenHelper testTokenHelper;

  private static final String BASE_PATH = Resources.ROOT_PATH + "/apps/dev_test/menus";
  private static final String TEST_SUFFIX = Long.toHexString(System.nanoTime());

  @Test
  void testFindMenusUnauthorized() {
    given()
      .when()
      .get(BASE_PATH)
      .then()
      .statusCode(401);
  }

  @Test
  void testFindTargets() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .get(BASE_PATH + "/targets")
      .then()
      .statusCode(200)
      .body("builtIn.size()", equalTo(6))
      .body("builtIn.id", hasItem("dashboard"))
      .body("entities.size()", greaterThanOrEqualTo(1))
      .body("entities.id", hasItem("Student"));
  }

  @Test
  void testMenuCrudOrderAndAppUpdatePreservesMenus() {
    String groupId = createMenu("""
      {
        "type": "GROUP",
        "name": "菜单测试分组-%s",
        "iconName": "Folder"
      }
      """.formatted(TEST_SUFFIX), 201);
    String childId = createMenu("""
      {
        "type": "MENU",
        "name": "菜单测试学生-%s",
        "iconName": "User",
        "parentId": "%s",
        "target": {"type": "ENTITY", "id": "Student"}
      }
      """.formatted(TEST_SUFFIX, groupId), 201);
    String builtInId = createMenu("""
      {
        "type": "MENU",
        "name": "菜单测试工作台-%s",
        "iconName": "Home",
        "target": {"type": "BUILT_IN", "id": "dashboard"}
      }
      """.formatted(TEST_SUFFIX), 201);

    try {
      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .when()
        .get(BASE_PATH)
        .then()
        .statusCode(200)
        .body("find { it.id == '%s' }.name".formatted(groupId), equalTo("菜单测试分组-" + TEST_SUFFIX))
        .body("find { it.id == '%s' }.children[0].pageRoute".formatted(groupId),
          equalTo("/apps/dev_test/" + childId));

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .contentType(ContentType.JSON)
        .body("""
            {
              "type": "MENU",
          "name": "菜单测试学生更新-%s",
              "iconName": "BookUser",
              "parentId": "%s",
              "target": {"type": "ENTITY", "id": "Student"}
            }
          """.formatted(TEST_SUFFIX, groupId))
        .when()
        .put(BASE_PATH + "/" + childId)
        .then()
        .statusCode(200)
        .body("id", equalTo(childId))
        .body("name", equalTo("菜单测试学生更新-" + TEST_SUFFIX))
        .body("target.id", equalTo("Student"))
        .body("pageRoute", equalTo("/apps/dev_test/" + childId));

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .contentType(ContentType.JSON)
        .body(Map.of(
          "root", List.of(builtInId, groupId),
          "children", Map.of(groupId, List.of(childId))
        ))
        .when()
        .put(BASE_PATH + "/order")
        .then()
        .statusCode(200)
        .body("[0].id", equalTo(builtInId))
        .body("[1].id", equalTo(groupId))
        .body("[1].children[0].id", equalTo(childId));

      Response app = given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .when()
        .get(Resources.ROOT_PATH + "/apps/dev_test");
      app.then().statusCode(200);
      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .contentType(ContentType.JSON)
        .body(Map.of(
          "name", app.body().path("name"),
          "description", app.body().path("description"),
          "iconName", app.body().path("iconName"),
          "primaryColor", app.body().path("primaryColor")
        ))
        .when()
        .put(Resources.ROOT_PATH + "/apps/dev_test")
        .then()
        .statusCode(200);

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .when()
        .get(BASE_PATH)
        .then()
        .statusCode(200)
        .body("find { it.id == '%s' }.children[0].id".formatted(groupId), equalTo(childId));

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .when()
        .delete(BASE_PATH + "/" + groupId)
        .then()
        .statusCode(204);

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .when()
        .get(BASE_PATH)
        .then()
        .statusCode(200)
        .body("id", not(hasItem(groupId)))
        .body("id", not(hasItem(childId)));
    } finally {
      deleteMenuQuietly(builtInId);
      deleteMenuQuietly(groupId);
      deleteMenuQuietly(childId);
    }
  }

  @Test
  void testCreateMenuValidations() {
    createMenu("""
      {
        "type": "MENU",
        "name": "缺少关联项",
        "iconName": "Home"
      }
      """, 400);
    createMenu("""
      {
        "type": "GROUP",
        "name": "分组不能关联",
        "target": {"type": "BUILT_IN", "id": "dashboard"}
      }
      """, 400);
    createMenu("""
      {
        "type": "MENU",
        "name": "父级不存在",
        "parentId": "not_exists",
        "target": {"type": "BUILT_IN", "id": "dashboard"}
      }
      """, 400);
    createMenu("""
      {
        "type": "MENU",
        "name": "非法图标",
        "iconName": "NotExists",
        "target": {"type": "BUILT_IN", "id": "dashboard"}
      }
      """, 400);
    createMenu("""
      {
        "type": "MENU",
        "name": "非法内置项",
        "target": {"type": "BUILT_IN", "id": "not_exists"}
      }
      """, 400);
    createMenu("""
      {
        "type": "MENU",
        "name": "非法实体",
        "target": {"type": "ENTITY", "id": "not_exists"}
      }
      """, 400);
  }

  @Test
  void testMissingMenuReturns404() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .put(BASE_PATH + "/not_exists")
      .then()
      .statusCode(404);

    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .delete(BASE_PATH + "/not_exists")
      .then()
      .statusCode(404);
  }

  private String createMenu(String body, int expectedStatus) {
    Response response = given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body(body)
      .when()
      .post(BASE_PATH);
    response.then().statusCode(expectedStatus);
    return expectedStatus == 201 ? response.body().path("id") : null;
  }

  private void deleteMenuQuietly(String menuId) {
    if (menuId == null) return;
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .delete(BASE_PATH + "/" + menuId);
  }
}
