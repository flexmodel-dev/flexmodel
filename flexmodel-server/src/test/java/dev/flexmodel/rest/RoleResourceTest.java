package dev.flexmodel.rest;

import dev.flexmodel.SQLiteTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * RoleResource 集成测试
 *
 * @author cjbi
 */
@QuarkusTest
@QuarkusTestResource(SQLiteTestResource.class)
public class RoleResourceTest {

  @Inject
  TestTokenHelper testTokenHelper;

  private static final String BASE_PATH = Resources.ROOT_PATH + "/roles";

  @BeforeEach
  void cleanupTestRole() {
    // 尝试删除测试创建的角色
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .delete(BASE_PATH + "/test_role_e2e")
      .then()
      .statusCode(anyOf(equalTo(204), equalTo(404), equalTo(500)));
  }

  /**
   * 测试获取角色列表
   */
  @Test
  void testFindAllRoles() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .get(BASE_PATH)
      .then()
      .statusCode(200)
      .body("size()", greaterThanOrEqualTo(1));
  }

  /**
   * 测试获取角色详情
   */
  @Test
  void testFindRoleById() {
    // 先获取角色列表，找到第一个角色的ID
    String roleId = given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .get(BASE_PATH)
      .then()
      .statusCode(200)
      .extract()
      .path("[0].id");

    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .get(BASE_PATH + "/" + roleId)
      .then()
      .statusCode(200)
      .body("id", equalTo(roleId))
      .body("name", notNullValue());
  }

  /**
   * 测试创建角色
   */
  @Test
  void testCreateRole() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body("""
        {
          "id": "test_role_e2e",
          "name": "E2E测试角色",
          "description": "用于集成测试的角色",
          "resourceIds": []
        }
        """)
      .when()
      .post(BASE_PATH)
      .then()
      .statusCode(200)
      .body("id", equalTo("test_role_e2e"))
      .body("name", equalTo("E2E测试角色"))
      .body("description", equalTo("用于集成测试的角色"));

    // 清理
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .delete(BASE_PATH + "/test_role_e2e")
      .then()
      .statusCode(204);
  }

  /**
   * 测试创建角色 - 带资源权限
   */
  @Test
  void testCreateRoleWithResources() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body("""
        {
          "id": "test_role_e2e",
          "name": "E2E角色带权限",
          "description": "带资源权限的角色",
          "resourceIds": ["1", "2"]
        }
        """)
      .when()
      .post(BASE_PATH)
      .then()
      .statusCode(200)
      .body("id", equalTo("test_role_e2e"))
      .body("name", equalTo("E2E角色带权限"));

    // 清理
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .delete(BASE_PATH + "/test_role_e2e")
      .then()
      .statusCode(204);
  }

  /**
   * 测试更新角色
   */
  @Test
  void testUpdateRole() {
    // 先创建角色
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body("""
        {
          "id": "test_role_e2e",
          "name": "E2E测试角色",
          "description": "用于集成测试的角色",
          "resourceIds": []
        }
        """)
      .when()
      .post(BASE_PATH)
      .then()
      .statusCode(200);

    // 更新角色
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body("""
        {
          "name": "更新后的角色名",
          "description": "更新后的描述",
          "resourceIds": []
        }
        """)
      .when()
      .put(BASE_PATH + "/test_role_e2e")
      .then()
      .statusCode(200)
      .body("id", equalTo("test_role_e2e"))
      .body("name", equalTo("更新后的角色名"))
      .body("description", equalTo("更新后的描述"));

    // 清理
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .delete(BASE_PATH + "/test_role_e2e")
      .then()
      .statusCode(204);
  }

  /**
   * 测试删除角色
   */
  @Test
  void testDeleteRole() {
    // 先创建角色
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body("""
        {
          "id": "test_role_e2e",
          "name": "待删除角色",
          "description": "用于删除测试的角色",
          "resourceIds": []
        }
        """)
      .when()
      .post(BASE_PATH)
      .then()
      .statusCode(200);

    // 删除角色
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .delete(BASE_PATH + "/test_role_e2e")
      .then()
      .statusCode(204);

    // 验证角色已被删除 - 列表中不包含该角色
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .get(BASE_PATH)
      .then()
      .statusCode(200)
      .body("findAll { it.id == 'test_role_e2e' }", hasSize(0));
  }

  /**
   * 测试完整的角色CRUD流程
   */
  @Test
  void testCompleteRoleCrudFlow() {
    // 1. 创建
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body("""
        {
          "id": "test_role_e2e",
          "name": "CRUD测试角色",
          "description": "完整CRUD流程测试",
          "resourceIds": []
        }
        """)
      .when()
      .post(BASE_PATH)
      .then()
      .statusCode(200)
      .body("name", equalTo("CRUD测试角色"));

    // 2. 查询详情
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .get(BASE_PATH + "/test_role_e2e")
      .then()
      .statusCode(200)
      .body("name", equalTo("CRUD测试角色"));

    // 3. 更新
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body("""
        {
          "name": "CRUD更新后",
          "description": "更新后的描述",
          "resourceIds": []
        }
        """)
      .when()
      .put(BASE_PATH + "/test_role_e2e")
      .then()
      .statusCode(200)
      .body("name", equalTo("CRUD更新后"));

    // 4. 删除
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .delete(BASE_PATH + "/test_role_e2e")
      .then()
      .statusCode(204);
  }
}
