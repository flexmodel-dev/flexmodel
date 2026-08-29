package dev.flexmodel.rest;

import dev.flexmodel.SQLiteTestResource;
import dev.flexmodel.auth.service.InternalTokenService;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * EdgeValidateResource 集成测试
 *
 * @author cjbi
 */
@QuarkusTest
@QuarkusTestResource(SQLiteTestResource.class)
public class EdgeValidateResourceTest {

  @Inject
  TestTokenHelper testTokenHelper;

  @Inject
  InternalTokenService internalTokenService;

  private static final String BASE_PATH = Resources.ROOT_PATH + "/edge";

  /**
   * 测试无效 token → fall through 到 anonymous（dev_test 无 IdP 配置）
   */
  @Test
  void testValidate_invalidToken_fallsThroughToAnonymous() {
    given()
      .contentType(ContentType.JSON)
      .accept(ContentType.JSON)
      .body("""
        {
          "token": "invalid-token",
          "projectId": "dev_test",
          "functionName": "hello-world"
        }
        """)
      .when()
      .post(BASE_PATH + "/validate")
      .then()
      .statusCode(200)
      // 无效 JWT → 不匹配 invoke-token → fall through → anonymous（dev_test 无 IdP）
      .body("valid", equalTo(true))
      .body("authType", equalTo("anonymous"));
  }

  /**
   * 测试空 token + 无 IdP 配置 → anonymous access
   */
  @Test
  void testValidate_anonymousAccess() {
    given()
      .contentType(ContentType.JSON)
      .accept(ContentType.JSON)
      .body("""
        {
          "token": "",
          "projectId": "dev_test",
          "functionName": "hello-world"
        }
        """)
      .when()
      .post(BASE_PATH + "/validate")
      .then()
      .statusCode(200)
      .body("valid", equalTo(true))
      .body("authType", equalTo("anonymous"))
      .body("projectId", equalTo("dev_test"))
      .body("authToken", notNullValue())
    ;
  }

  /**
   * 测试 null token + 无 IdP 配置 → anonymous access
   */
  @Test
  void testValidate_nullTokenAnonymous() {
    given()
      .contentType(ContentType.JSON)
      .accept(ContentType.JSON)
      .body("""
        {
          "projectId": "dev_test",
          "functionName": "hello-world"
        }
        """)
      .when()
      .post(BASE_PATH + "/validate")
      .then()
      .statusCode(200)
      .body("valid", equalTo(true))
      .body("authType", equalTo("anonymous"));
  }

  /**
   * 测试 API Key 格式但无效 → 返回 valid=false（不 fall through）
   */
  @Test
  void testValidate_invalidApiKey() {
    given()
      .contentType(ContentType.JSON)
      .accept(ContentType.JSON)
      .body("""
        {
          "token": "fm_ak_invalid_key_12345",
          "projectId": "dev_test",
          "functionName": "hello-world"
        }
        """)
      .when()
      .post(BASE_PATH + "/validate")
      .then()
      .statusCode(200)
      .body("valid", equalTo(false));
  }

  /**
   * 测试系统 JWT（非 svc:invoke）→ 不匹配 invoke-token，fall through 到 anonymous
   */
  @Test
  void testValidate_systemJwt_fallsThrough() {
    // 系统用户 JWT（account = "admin"），不是 svc:invoke
    String systemJwt = testTokenHelper.getTestToken("admin");

    given()
      .contentType(ContentType.JSON)
      .accept(ContentType.JSON)
      .body("""
        {
          "token": "%s",
          "projectId": "dev_test",
          "functionName": "hello-world"
        }
        """.formatted(systemJwt))
      .when()
      .post(BASE_PATH + "/validate")
      .then()
      .statusCode(200)
      // 非 svc:invoke JWT → fall through → anonymous（dev_test 无 IdP 配置）
      .body("valid", equalTo(true))
      .body("authType", equalTo("anonymous"));
  }

  /**
   * 测试 internal token（svc:runtime）→ 不匹配 invoke-token，fall through
   */
  @Test
  void testValidate_runtimeToken_fallsThrough() {
    String runtimeToken = internalTokenService.signToken("dev_test");

    given()
      .contentType(ContentType.JSON)
      .accept(ContentType.JSON)
      .body("""
        {
          "token": "%s",
          "projectId": "dev_test",
          "functionName": "hello-world"
        }
        """.formatted(runtimeToken))
      .when()
      .post(BASE_PATH + "/validate")
      .then()
      .statusCode(200)
      .body("valid", equalTo(true))
      .body("authType", equalTo("anonymous"));
  }

  /**
   * 测试缺少 projectId → fall through 到 anonymous（无 projectId 无法匹配 IdP）
   */
  @Test
  void testValidate_missingProjectId() {
    given()
      .contentType(ContentType.JSON)
      .accept(ContentType.JSON)
      .body("""
        {
          "token": "some-token",
          "functionName": "hello-world"
        }
        """)
      .when()
      .post(BASE_PATH + "/validate")
      .then()
      .statusCode(200)
      // 无 projectId → 无法匹配 invoke-token（projectId 在 claim 中）→ 无法匹配 IdP → invalid
      .body("valid", equalTo(false));
  }
}
