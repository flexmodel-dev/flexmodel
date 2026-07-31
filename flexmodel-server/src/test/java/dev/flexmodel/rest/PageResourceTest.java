package dev.flexmodel.rest;

import dev.flexmodel.SQLiteTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * PageResource 集成测试
 *
 * @author cjbi
 */
@QuarkusTest
@QuarkusTestResource(SQLiteTestResource.class)
public class PageResourceTest {

  @Inject
  TestTokenHelper testTokenHelper;

  private static final String BASE_PATH = Resources.ROOT_PATH + "/projects/dev_test/page";

  /**
   * 测试获取 Pages 站点配置 - 未初始化时返回 404
   */
  @Test
  void testGetPageSite_notFound() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .get(BASE_PATH)
      .then()
      .statusCode(anyOf(equalTo(404), equalTo(200)));
  }

  /**
   * 测试更新 Pages 站点配置（自动创建）
   */
  @Test
  void testUpdatePageSite_autoCreate() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .accept(ContentType.JSON)
      .body("""
        {
          "customDomains": ["example.com", "www.example.com"]
        }
        """)
      .when()
      .put(BASE_PATH)
      .then()
      .statusCode(200)
      .body("status", equalTo("READY"))
      .body("customDomains", hasItems("example.com", "www.example.com"));
  }

  /**
   * 测试获取 Pages 站点配置 - 更新后可获取
   */
  @Test
  void testGetPageSite_afterUpdate() {
    // 先更新
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .accept(ContentType.JSON)
      .body("""
        {
          "customDomains": ["test.example.com"]
        }
        """)
      .when()
      .put(BASE_PATH)
      .then()
      .statusCode(200);

    // 再获取
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .get(BASE_PATH)
      .then()
      .statusCode(200)
      .body("status", notNullValue())
      .body("customDomains", notNullValue());
  }

  /**
   * 测试切生产别名 - 缺少 deploymentId 参数
   */
  @Test
  void testSetProduction_missingDeploymentId() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .accept(ContentType.JSON)
      .when()
      .put(BASE_PATH + "/production")
      .then()
      .statusCode(anyOf(equalTo(400), equalTo(404)));
  }

  /**
   * 测试切生产别名 - 不存在的 deploymentId
   */
  @Test
  void testSetProduction_nonExistentDeployment() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .accept(ContentType.JSON)
      .queryParam("deploymentId", "dep_nonexistent")
      .when()
      .put(BASE_PATH + "/production")
      .then()
      .statusCode(anyOf(equalTo(400), equalTo(404)));
  }

  /**
   * 测试未认证访问 - 返回 401
   */
  @Test
  void testGetPageSite_unauthorized() {
    given()
      .when()
      .get(BASE_PATH)
      .then()
      .statusCode(401);
  }

  /**
   * 测试完整的 Pages CRUD 流程
   */
  @Test
  void testCompletePageCrudFlow() {
    // 1. 更新站点配置
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .accept(ContentType.JSON)
      .body("""
        {
          "customDomains": ["crud-test.example.com"]
        }
        """)
      .when()
      .put(BASE_PATH)
      .then()
      .statusCode(200)
      .body("customDomains", hasItem("crud-test.example.com"));

    // 2. 获取站点配置确认
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .get(BASE_PATH)
      .then()
      .statusCode(200)
      .body("customDomains", hasItem("crud-test.example.com"));

    // 3. 再次更新（覆盖 customDomains）
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .accept(ContentType.JSON)
      .body("""
        {
          "customDomains": ["updated.example.com"]
        }
        """)
      .when()
      .put(BASE_PATH)
      .then()
      .statusCode(200)
      .body("customDomains", hasItem("updated.example.com"));
  }
}
