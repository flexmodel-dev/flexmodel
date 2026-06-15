package dev.flexmodel.rest;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import dev.flexmodel.SQLiteTestResource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * ApiKeyResource 集成测试
 *
 * @author cjbi
 */
@QuarkusTest
@QuarkusTestResource(SQLiteTestResource.class)
public class ApiKeyResourceTest {

  private static final String BASE_PATH = Resources.ROOT_PATH + "/api-keys";

  /**
   * 测试获取API Key列表
   */
  @Test
  void testListApiKeys() {
    given()
      .header("Authorization", TestTokenHelper.getAuthorizationHeader())
      .when()
      .get(BASE_PATH)
      .then()
      .statusCode(200);
  }

  /**
   * 测试创建API Key
   */
  @Test
  void testCreateApiKey() {
    String apiKeyId = given()
      .header("Authorization", TestTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .accept(ContentType.JSON)
      .body("""
        {
          "name": "E2E测试API Key",
          "keyType": "user",
          "scopes": "read,write",
          "projectIds": "dev_test",
          "readOnly": false
        }
        """)
      .when()
      .post(BASE_PATH)
      .then()
      .statusCode(200)
      .body("id", notNullValue())
      .body("name", equalTo("E2E测试API Key"))
      .body("keyPrefix", notNullValue())
      .extract()
      .path("id");

    // 清理
    given()
      .header("Authorization", TestTokenHelper.getAuthorizationHeader())
      .when()
      .delete(BASE_PATH + "/" + apiKeyId)
      .then()
      .statusCode(204);
  }

  /**
   * 测试创建只读API Key
   */
  @Test
  void testCreateReadOnlyApiKey() {
    String apiKeyId = given()
      .header("Authorization", TestTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .accept(ContentType.JSON)
      .body("""
        {
          "name": "E2E只读Key",
          "keyType": "user",
          "scopes": "read",
          "projectIds": "dev_test",
          "readOnly": true
        }
        """)
      .when()
      .post(BASE_PATH)
      .then()
      .statusCode(200)
      .body("readOnly", equalTo(true))
      .extract()
      .path("id");

    // 清理
    given()
      .header("Authorization", TestTokenHelper.getAuthorizationHeader())
      .when()
      .delete(BASE_PATH + "/" + apiKeyId)
      .then()
      .statusCode(204);
  }

  /**
   * 测试重新生成API Key
   */
  @Test
  void testRegenerateApiKey() {
    // 先创建API Key
    String apiKeyId = given()
      .header("Authorization", TestTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .accept(ContentType.JSON)
      .body("""
        {
          "name": "E2E重新生成Key",
          "keyType": "user",
          "scopes": "read",
          "projectIds": "dev_test",
          "readOnly": false
        }
        """)
      .when()
      .post(BASE_PATH)
      .then()
      .statusCode(200)
      .extract()
      .path("id");

    // 重新生成 - POST请求带空body但需要JSON content type
    given()
      .header("Authorization", TestTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .accept(ContentType.JSON)
      .body("{}")
      .when()
      .post(BASE_PATH + "/" + apiKeyId + "/regenerate")
      .then()
      .statusCode(200)
      .body("id", equalTo(apiKeyId))
      .body("key", notNullValue())
      .body("keyPrefix", notNullValue());

    // 清理
    given()
      .header("Authorization", TestTokenHelper.getAuthorizationHeader())
      .when()
      .delete(BASE_PATH + "/" + apiKeyId)
      .then()
      .statusCode(204);
  }

  /**
   * 测试删除API Key
   */
  @Test
  void testDeleteApiKey() {
    // 先创建API Key
    String apiKeyId = given()
      .header("Authorization", TestTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .accept(ContentType.JSON)
      .body("""
        {
          "name": "E2E待删除Key",
          "keyType": "user",
          "scopes": "read",
          "projectIds": "dev_test",
          "readOnly": false
        }
        """)
      .when()
      .post(BASE_PATH)
      .then()
      .statusCode(200)
      .extract()
      .path("id");

    // 删除API Key
    given()
      .header("Authorization", TestTokenHelper.getAuthorizationHeader())
      .when()
      .delete(BASE_PATH + "/" + apiKeyId)
      .then()
      .statusCode(204);

    // 验证已被删除 - 列表中不包含该key
    given()
      .header("Authorization", TestTokenHelper.getAuthorizationHeader())
      .when()
      .get(BASE_PATH)
      .then()
      .statusCode(200)
      .body("findAll { it.id == '%s' }".formatted(apiKeyId), hasSize(0));
  }

  /**
   * 测试完整的API Key CRUD流程
   */
  @Test
  void testCompleteApiKeyCrudFlow() {
    // 1. 创建
    String apiKeyId = given()
      .header("Authorization", TestTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .accept(ContentType.JSON)
      .body("""
        {
          "name": "E2E CRUD Key",
          "keyType": "user",
          "scopes": "read,write",
          "projectIds": "dev_test",
          "readOnly": false
        }
        """)
      .when()
      .post(BASE_PATH)
      .then()
      .statusCode(200)
      .body("name", equalTo("E2E CRUD Key"))
      .extract()
      .path("id");

    // 2. 查看列表确认存在
    given()
      .header("Authorization", TestTokenHelper.getAuthorizationHeader())
      .when()
      .get(BASE_PATH)
      .then()
      .statusCode(200)
      .body("findAll { it.id == '%s' }".formatted(apiKeyId), hasSize(1));

    // 3. 重新生成
    given()
      .header("Authorization", TestTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .accept(ContentType.JSON)
      .body("{}")
      .when()
      .post(BASE_PATH + "/" + apiKeyId + "/regenerate")
      .then()
      .statusCode(200)
      .body("id", equalTo(apiKeyId));

    // 4. 删除
    given()
      .header("Authorization", TestTokenHelper.getAuthorizationHeader())
      .when()
      .delete(BASE_PATH + "/" + apiKeyId)
      .then()
      .statusCode(204);
  }
}
