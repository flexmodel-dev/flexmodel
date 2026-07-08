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
 * AuthProviderConfigResource 集成测试
 *
 * @author cjbi
 */
@QuarkusTest
@QuarkusTestResource(SQLiteTestResource.class)
public class AuthProviderConfigResourceTest {

  @Inject
  TestTokenHelper testTokenHelper;

  private static final String BASE_PATH = Resources.ROOT_PATH + "/projects/dev_test/auth-providers";
  private static final String TEST_PROVIDER_NAME = "test_provider_e2e";

  @BeforeEach
  void cleanupTestProvider() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .delete(BASE_PATH + "/" + TEST_PROVIDER_NAME)
      .then()
      .statusCode(anyOf(equalTo(204), equalTo(404)));
  }

  /**
   * 测试创建OIDC认证提供商
   */
  @Test
  void testCreateOidcAuthProvider() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body("""
        {
          "name": "test_provider_e2e",
          "type": "oidc",
          "enabled": true,
          "config": {
            "issuer": "http://test-issuer",
            "clientId": "test_client",
            "clientSecret": "test_secret"
          }
        }
        """)
      .when()
      .post(BASE_PATH)
      .then()
      .statusCode(200)
      .body("name", equalTo("test_provider_e2e"))
      .body("type", equalTo("oidc"))
      .body("enabled", equalTo(true));

    // 清理
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .delete(BASE_PATH + "/" + TEST_PROVIDER_NAME)
      .then()
      .statusCode(204);
  }

  /**
   * 测试创建Function认证提供商
   */
  @Test
  void testCreateFunctionAuthProvider() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body("""
        {
          "name": "test_provider_e2e",
          "type": "function",
          "enabled": true,
          "config": {
            "functionName": "test-auth"
          }
        }
        """)
      .when()
      .post(BASE_PATH)
      .then()
      .statusCode(200)
      .body("name", equalTo("test_provider_e2e"))
      .body("type", equalTo("function"));

    // 清理
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .delete(BASE_PATH + "/" + TEST_PROVIDER_NAME)
      .then()
      .statusCode(204);
  }

  /**
   * 测试更新认证提供商
   */
  @Test
  void testUpdateAuthProvider() {
    // 先创建
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body("""
        {
          "name": "test_provider_e2e",
          "type": "oidc",
          "enabled": true,
          "config": {
            "issuer": "http://original-issuer",
            "clientId": "original_client",
            "clientSecret": "original_secret"
          }
        }
        """)
      .when()
      .post(BASE_PATH)
      .then()
      .statusCode(200);

    // 更新
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body("""
        {
          "name": "test_provider_e2e",
          "type": "oidc",
          "enabled": false,
          "config": {
            "issuer": "http://updated-issuer",
            "clientId": "updated_client",
            "clientSecret": "updated_secret"
          }
        }
        """)
      .when()
      .put(BASE_PATH + "/" + TEST_PROVIDER_NAME)
      .then()
      .statusCode(200)
      .body("name", equalTo("test_provider_e2e"))
      .body("enabled", equalTo(false));

    // 清理
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .delete(BASE_PATH + "/" + TEST_PROVIDER_NAME)
      .then()
      .statusCode(204);
  }

  /**
   * 测试删除认证提供商
   */
  @Test
  void testDeleteAuthProvider() {
    // 先创建
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body("""
        {
          "name": "test_provider_e2e",
          "type": "function",
          "enabled": true,
          "config": {
            "functionName": "test-auth"
          }
        }
        """)
      .when()
      .post(BASE_PATH)
      .then()
      .statusCode(200);

    // 删除
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .delete(BASE_PATH + "/" + TEST_PROVIDER_NAME)
      .then()
      .statusCode(204);

    // 验证已被删除
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .get(BASE_PATH)
      .then()
      .statusCode(200)
      .body("findAll { it.name == 'test_provider_e2e' }", hasSize(0));
  }

  /**
   * 测试完整的认证提供商CRUD流程
   */
  @Test
  void testCompleteAuthProviderCrudFlow() {
    // 1. 创建
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body("""
        {
          "name": "test_provider_e2e",
          "type": "oidc",
          "enabled": true,
          "config": {
            "issuer": "http://crud-issuer",
            "clientId": "crud_client",
            "clientSecret": "crud_secret"
          }
        }
        """)
      .when()
      .post(BASE_PATH)
      .then()
      .statusCode(200)
      .body("name", equalTo("test_provider_e2e"));

    // 2. 列表确认存在
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .get(BASE_PATH)
      .then()
      .statusCode(200)
      .body("findAll { it.name == 'test_provider_e2e' }", hasSize(1));

    // 3. 更新
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body("""
        {
          "name": "test_provider_e2e",
          "type": "oidc",
          "enabled": false,
          "config": {
            "issuer": "http://updated-issuer"
          }
        }
        """)
      .when()
      .put(BASE_PATH + "/" + TEST_PROVIDER_NAME)
      .then()
      .statusCode(200)
      .body("enabled", equalTo(false));

    // 4. 删除
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .delete(BASE_PATH + "/" + TEST_PROVIDER_NAME)
      .then()
      .statusCode(204);
  }
}
