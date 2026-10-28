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
      .body("builtIn.size()", equalTo(3))
      .body("builtIn.id", hasItem("workbench"))
      .body("builtIn.id", not(hasItem("feeds")))
      .body("builtIn.id", not(hasItem("contacts")))
      .body("builtIn.id", not(hasItem("notifications")))
      .body("forms.size()", greaterThanOrEqualTo(1))
      .body("forms.id", hasItem("Student"))
      .body("forms.label", hasItem("学生"));
  }

  @Test
  void testCreateBuiltInMenuGeneratesPageRoute() {
    String groupId = createMenu("""
      {
        "type": "GROUP",
        "name": "内置页面分组-%s"
      }
      """.formatted(TEST_SUFFIX), 201);
    String menuId = createMenu("""
      {
        "type": "ITEM",
        "name": "工作台-%s",
        "parentId": "%s",
        "target": {"type": "BUILTIN", "id": "workbench"}
      }
      """.formatted(TEST_SUFFIX, groupId), 201);

    try {
      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .when()
        .get(BASE_PATH)
        .then()
        .statusCode(200)
        .body("find { it.id == '%s' }.children.find { it.id == '%s' }.target.type".formatted(groupId, menuId),
          equalTo("BUILTIN"))
        .body("find { it.id == '%s' }.children.find { it.id == '%s' }.target.id".formatted(groupId, menuId),
          equalTo("workbench"))
        .body("find { it.id == '%s' }.children.find { it.id == '%s' }.pageRoute".formatted(groupId, menuId),
          equalTo("/apps/dev_test/" + menuId));
    } finally {
      deleteMenuQuietly(groupId);
    }
  }

  @Test
  void testCreateAndUpdateLinkMenu() {
    String menuId = createMenu("""
      {
        "type": "ITEM",
        "name": "自定义链接-%s",
        "target": {"type": "LINK", "id": "https://example.com/path?from=flexmodel"}
      }
      """.formatted(TEST_SUFFIX), 201);

    try {
      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .contentType(ContentType.JSON)
        .body("""
          {
            "type": "ITEM",
            "name": "自定义链接-%s",
            "target": {"type": "LINK", "id": "https://docs.example.com/flexmodel"}
          }
          """.formatted(TEST_SUFFIX))
        .when()
        .put(BASE_PATH + "/" + menuId)
        .then()
        .statusCode(200)
        .body("target.type", equalTo("LINK"))
        .body("target.id", equalTo("https://docs.example.com/flexmodel"))
        .body("target.label", equalTo("https://docs.example.com/flexmodel"))
        .body("pageRoute", equalTo("/apps/dev_test/" + menuId));

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .when()
        .get(BASE_PATH)
        .then()
        .statusCode(200)
        .body("find { it.id == '%s' }.target.type".formatted(menuId), equalTo("LINK"))
        .body("find { it.id == '%s' }.target.id".formatted(menuId),
          equalTo("https://docs.example.com/flexmodel"));
    } finally {
      deleteMenuQuietly(menuId);
    }
  }

  @Test
  void testMoveItemBetweenGroups() {
    String sourceGroupId = createMenu("""
      {
        "type": "GROUP",
        "name": "移动源分组-%s"
      }
      """.formatted(TEST_SUFFIX), 201);
    String targetGroupId = createMenu("""
      {
        "type": "GROUP",
        "name": "移动目标分组-%s"
      }
      """.formatted(TEST_SUFFIX), 201);
    String itemId = createMenu("""
      {
        "type": "ITEM",
        "name": "移动菜单-%s",
        "parentId": "%s",
        "target": {"type": "BUILTIN", "id": "files"}
      }
      """.formatted(TEST_SUFFIX, sourceGroupId), 201);

    try {
      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .contentType(ContentType.JSON)
        .body("""
          {
            "type": "ITEM",
            "name": "移动菜单-%s",
            "parentId": "%s",
            "target": {"type": "BUILTIN", "id": "files"}
          }
          """.formatted(TEST_SUFFIX, targetGroupId))
        .when()
        .put(BASE_PATH + "/" + itemId)
        .then()
        .statusCode(200)
        .body("parentId", equalTo(targetGroupId));

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .when()
        .get(BASE_PATH)
        .then()
        .statusCode(200)
        .body("find { it.id == '%s' }.children.collect { it.id }".formatted(sourceGroupId),
          not(hasItem(itemId)))
        .body("find { it.id == '%s' }.children.collect { it.id }".formatted(targetGroupId),
          hasItem(itemId));
    } finally {
      deleteMenuQuietly(sourceGroupId);
      deleteMenuQuietly(targetGroupId);
    }
  }

  @Test
  void testMoveItemOutOfGroup() {
    String rootGroupId = createMenu("""
      {
        "type": "GROUP",
        "name": "移出根分组-%s"
      }
      """.formatted(TEST_SUFFIX), 201);
    String nestedGroupId = createMenu("""
      {
        "type": "GROUP",
        "name": "移出嵌套分组-%s",
        "parentId": "%s"
      }
      """.formatted(TEST_SUFFIX, rootGroupId), 201);
    String itemId = createMenu("""
      {
        "type": "ITEM",
        "name": "移出菜单-%s",
        "parentId": "%s",
        "target": {"type": "BUILTIN", "id": "files"}
      }
      """.formatted(TEST_SUFFIX, nestedGroupId), 201);

    try {
      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .contentType(ContentType.JSON)
        .body("""
          {
            "type": "ITEM",
            "name": "移出菜单-%s",
            "parentId": "%s",
            "target": {"type": "BUILTIN", "id": "files"}
          }
          """.formatted(TEST_SUFFIX, rootGroupId))
        .when()
        .put(BASE_PATH + "/" + itemId)
        .then()
        .statusCode(200)
        .body("parentId", equalTo(rootGroupId));

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .when()
        .get(BASE_PATH)
        .then()
        .statusCode(200)
        .body("find { it.id == '%s' }.children.collect { it.id }".formatted(nestedGroupId),
          not(hasItem(itemId)))
        .body("find { it.id == '%s' }.children.collect { it.id }".formatted(rootGroupId),
          hasItem(itemId));
    } finally {
      deleteMenuQuietly(rootGroupId);
      deleteMenuQuietly(nestedGroupId);
    }
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
        "type": "ITEM",
        "name": "菜单测试学生-%s",
        "iconName": "User",
        "parentId": "%s",
        "target": {"type": "FORM", "id": "Student"}
      }
      """.formatted(TEST_SUFFIX, groupId), 201);
    String nestedGroupId = createMenu("""
      {
        "type": "GROUP",
        "name": "菜单测试嵌套分组-%s",
        "iconName": "Folder",
        "parentId": "%s"
      }
      """.formatted(TEST_SUFFIX, groupId), 201);
    String nestedChildId = createMenu("""
      {
        "type": "ITEM",
        "name": "菜单测试嵌套学生-%s",
        "iconName": "User",
        "parentId": "%s",
        "target": {"type": "FORM", "id": "Student"}
      }
      """.formatted(TEST_SUFFIX, nestedGroupId), 201);
    String builtInId = createMenu("""
      {
        "type": "ITEM",
        "name": "菜单测试工作台-%s",
        "iconName": "Home",
        "target": {"type": "BUILTIN", "id": "files"}
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
        .when()
        .get(BASE_PATH)
        .then()
        .statusCode(200)
        .body("find { it.id == '%s' }.children.find { it.id == '%s' }.children[0].pageRoute".formatted(groupId, nestedGroupId),
          equalTo("/apps/dev_test/" + nestedChildId));

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .contentType(ContentType.JSON)
        .body("""
            {
              "type": "ITEM",
          "name": "菜单测试学生更新-%s",
              "iconName": "BookUser",
              "parentId": "%s",
              "target": {"type": "FORM", "id": "Student"}
            }
          """.formatted(TEST_SUFFIX, groupId))
        .when()
        .put(BASE_PATH + "/" + childId)
        .then()
        .statusCode(200)
        .body("id", equalTo(childId))
        .body("name", equalTo("菜单测试学生更新-" + TEST_SUFFIX))
        .body("target.id", equalTo("Student"))
        .body("target.label", equalTo("学生"))
        .body("target.type", equalTo("FORM"))
        .body("pageRoute", equalTo("/apps/dev_test/" + childId));

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .when()
        .get(BASE_PATH)
        .then()
        .statusCode(200)
        .body("find { it.id == '%s' }.children[0].id".formatted(groupId), equalTo(childId));

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .contentType(ContentType.JSON)
        .body(Map.of(
          "root", List.of(builtInId, groupId),
          "children", Map.of(
            groupId, List.of(childId, nestedGroupId),
            nestedGroupId, List.of(nestedChildId)
          )
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
        "type": "ITEM",
        "name": "缺少关联项",
        "iconName": "Home"
      }
      """, 400);
    createMenu("""
      {
        "type": "GROUP",
        "name": "分组不能关联",
        "target": {"type": "BUILTIN", "id": "files"}
      }
      """, 400);
    createMenu("""
      {
        "type": "ITEM",
        "name": "父级不存在",
        "parentId": "not_exists",
        "target": {"type": "BUILTIN", "id": "files"}
      }
      """, 400);
    createMenu("""
      {
        "type": "ITEM",
        "name": "非法图标",
        "iconName": "NotExists",
        "target": {"type": "BUILTIN", "id": "files"}
      }
      """, 400);
    createMenu("""
      {
        "type": "ITEM",
        "name": "非法内置项",
        "target": {"type": "BUILTIN", "id": "not_exists"}
      }
      """, 400);
    createMenu("""
      {
        "type": "ITEM",
        "name": "非法表单",
        "target": {"type": "FORM", "id": "not_exists"}
      }
      """, 400);
    createMenu("""
      {
        "type": "ITEM",
        "name": "非法链接协议",
        "target": {"type": "LINK", "id": "javascript:alert(1)"}
      }
      """, 400);
    createMenu("""
      {
        "type": "ITEM",
        "name": "非法链接传输协议",
        "target": {"type": "LINK", "id": "ftp://example.com"}
      }
      """, 400);
    createMenu("""
      {
        "type": "ITEM",
        "name": "链接缺少主机",
        "target": {"type": "LINK", "id": "https:///path"}
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

  @Test
  void testIndependentMemberConfigurationSurvivesEditingAndOrdering() {
    String id = createMenu("""
      {"type":"GROUP","name":"授权测试-%s"}
      """.formatted(TEST_SUFFIX), 201);
    var members = List.of(Map.of("type", "USER", "id", "admin"),
      Map.of("type", "ROLE", "id", "admin"),
      Map.of("type", "ORGANIZATION", "id", "org-test", "includeChildren", true));
    try {
      given().header("Authorization", testTokenHelper.getAuthorizationHeader()).contentType(ContentType.JSON)
        .body(Map.of("members", members)).patch(BASE_PATH + "/" + id).then().statusCode(200)
        .body("members.size()", equalTo(3)).body("members[2].includeChildren", equalTo(true))
        .body("ownerId", equalTo("admin"));
      given().header("Authorization", testTokenHelper.getAuthorizationHeader()).contentType(ContentType.JSON)
        .body(Map.of("type", "GROUP", "name", "授权测试更新-" + TEST_SUFFIX))
        .put(BASE_PATH + "/" + id).then().statusCode(200).body("members.size()", equalTo(3));
      List<String> roots = given().header("Authorization", testTokenHelper.getAuthorizationHeader())
        .get(BASE_PATH).body().path("id");
      given().header("Authorization", testTokenHelper.getAuthorizationHeader()).contentType(ContentType.JSON)
        .body(Map.of("root", roots, "children", Map.of(id, List.of())))
        .put(BASE_PATH + "/order").then().statusCode(200)
        .body("find { it.id == '%s' }.members.size()".formatted(id), equalTo(3));
      given().header("Authorization", testTokenHelper.getAuthorizationHeader()).get(BASE_PATH).then()
        .statusCode(200).body("find { it.id == '%s' }.members.id".formatted(id), hasItems("admin", "org-test"))
        .body("find { it.id == '%s' }.ownerId".formatted(id), equalTo("admin"));
      given().header("Authorization", testTokenHelper.getAuthorizationHeader()).contentType(ContentType.JSON)
        .body(Map.of("members", List.of(Map.of("type", "BAD", "id", "admin"))))
        .patch(BASE_PATH + "/" + id).then().statusCode(400);
      given().header("Authorization", testTokenHelper.getAuthorizationHeader()).contentType(ContentType.JSON)
        .body(Map.of("members", List.of(Map.of("type", "USER", "id", "admin", "includeChildren", true))))
        .patch(BASE_PATH + "/" + id).then().statusCode(400);
      given().header("Authorization", testTokenHelper.getAuthorizationHeader()).contentType(ContentType.JSON)
        .body(Map.of("members", "admin")).patch(BASE_PATH + "/" + id).then().statusCode(400);
      given().header("Authorization", testTokenHelper.getAuthorizationHeader()).contentType(ContentType.JSON)
        .body(Map.of("unknown", "value")).patch(BASE_PATH + "/" + id).then().statusCode(400);
      given().header("Authorization", testTokenHelper.getAuthorizationHeader()).contentType(ContentType.JSON)
        .body(Map.of("members", List.of())).patch(BASE_PATH + "/" + id).then().statusCode(200)
        .body("members.size()", equalTo(0)).body("name", equalTo("授权测试更新-" + TEST_SUFFIX));
    } finally {
      deleteMenuQuietly(id);
    }
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
