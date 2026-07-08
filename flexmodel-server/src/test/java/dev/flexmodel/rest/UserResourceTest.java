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
 * UserResource 集成测试
 *
 * @author cjbi
 */
@QuarkusTest
@QuarkusTestResource(SQLiteTestResource.class)
public class UserResourceTest {

  @Inject
  TestTokenHelper testTokenHelper;

  private static final String BASE_PATH = Resources.ROOT_PATH + "/users";
  private static final String TEST_USER_ID = "test_user_e2e";

  @BeforeEach
  void cleanupTestUser() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .delete(BASE_PATH + "/" + TEST_USER_ID)
      .then()
      .statusCode(anyOf(equalTo(204), equalTo(404), equalTo(500)));
  }

  /**
   * 测试获取用户列表
   */
  @Test
  void testFindAllUsers() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .get(BASE_PATH)
      .then()
      .statusCode(200)
      .body("size()", greaterThanOrEqualTo(1));
  }

  /**
   * 测试获取用户详情 - 已存在的用户
   */
  @Test
  void testFindUserById() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .get(BASE_PATH + "/admin")
      .then()
      .statusCode(200)
      .body("id", equalTo("admin"))
      .body("name", notNullValue());
  }

  /**
   * 测试获取用户详情 - 不存在的用户（返回204或null）
   */
  @Test
  void testFindUserByIdNotFound() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .get(BASE_PATH + "/nonexistent_user")
      .then()
      .statusCode(anyOf(equalTo(204), equalTo(200), equalTo(404)));
  }

  /**
   * 测试创建用户
   */
  @Test
  void testCreateUser() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body("""
        {
          "id": "test_user_e2e",
          "name": "E2E测试用户",
          "email": "e2e@test.com",
          "password": "test123",
          "roleIds": []
        }
        """)
      .when()
      .post(BASE_PATH)
      .then()
      .statusCode(200)
      .body("id", equalTo("test_user_e2e"))
      .body("name", equalTo("E2E测试用户"))
      .body("email", equalTo("e2e@test.com"));

    // 清理
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .delete(BASE_PATH + "/" + TEST_USER_ID)
      .then()
      .statusCode(204);
  }

  /**
   * 测试创建用户 - 带角色
   */
  @Test
  void testCreateUserWithRoles() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body("""
        {
          "id": "test_user_e2e",
          "name": "E2E用户带角色",
          "email": "e2e_role@test.com",
          "password": "test123",
          "roleIds": ["admin"]
        }
        """)
      .when()
      .post(BASE_PATH)
      .then()
      .statusCode(200)
      .body("id", equalTo("test_user_e2e"))
      .body("name", equalTo("E2E用户带角色"));

    // 清理
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .delete(BASE_PATH + "/" + TEST_USER_ID)
      .then()
      .statusCode(204);
  }

  /**
   * 测试更新用户
   */
  @Test
  void testUpdateUser() {
    // 先创建用户
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body("""
        {
          "id": "test_user_e2e",
          "name": "E2E测试用户",
          "email": "e2e@test.com",
          "password": "test123",
          "roleIds": []
        }
        """)
      .when()
      .post(BASE_PATH)
      .then()
      .statusCode(200);

    // 更新用户
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body("""
        {
          "name": "更新后的用户名",
          "email": "updated@test.com",
          "roleIds": []
        }
        """)
      .when()
      .put(BASE_PATH + "/" + TEST_USER_ID)
      .then()
      .statusCode(200)
      .body("id", equalTo("test_user_e2e"))
      .body("name", equalTo("更新后的用户名"))
      .body("email", equalTo("updated@test.com"));

    // 清理
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .delete(BASE_PATH + "/" + TEST_USER_ID)
      .then()
      .statusCode(204);
  }

  /**
   * 测试删除用户
   */
  @Test
  void testDeleteUser() {
    // 先创建用户
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body("""
        {
          "id": "test_user_e2e",
          "name": "待删除用户",
          "email": "delete@test.com",
          "password": "test123",
          "roleIds": []
        }
        """)
      .when()
      .post(BASE_PATH)
      .then()
      .statusCode(200);

    // 删除用户
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .delete(BASE_PATH + "/" + TEST_USER_ID)
      .then()
      .statusCode(204);

    // 验证用户已被删除
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .get(BASE_PATH)
      .then()
      .statusCode(200)
      .body("findAll { it.id == 'test_user_e2e' }", hasSize(0));
  }

  /**
   * 测试完整的用户CRUD流程
   */
  @Test
  void testCompleteUserCrudFlow() {
    // 1. 创建
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body("""
        {
          "id": "test_user_e2e",
          "name": "CRUD测试用户",
          "email": "crud@test.com",
          "password": "test123",
          "roleIds": []
        }
        """)
      .when()
      .post(BASE_PATH)
      .then()
      .statusCode(200)
      .body("name", equalTo("CRUD测试用户"));

    // 2. 查询详情
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .get(BASE_PATH + "/" + TEST_USER_ID)
      .then()
      .statusCode(200)
      .body("name", equalTo("CRUD测试用户"));

    // 3. 更新
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body("""
        {
          "name": "CRUD更新后",
          "email": "crud_updated@test.com",
          "roleIds": []
        }
        """)
      .when()
      .put(BASE_PATH + "/" + TEST_USER_ID)
      .then()
      .statusCode(200)
      .body("name", equalTo("CRUD更新后"));

    // 4. 删除
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .delete(BASE_PATH + "/" + TEST_USER_ID)
      .then()
      .statusCode(204);
  }
}
