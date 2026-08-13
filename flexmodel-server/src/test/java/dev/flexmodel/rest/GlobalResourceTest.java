package dev.flexmodel.rest;

import dev.flexmodel.SQLiteTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * GlobalResource 集成测试
 *
 * @author cjbi
 */
@QuarkusTest
@QuarkusTestResource(SQLiteTestResource.class)
public class GlobalResourceTest {

  @Inject
  TestTokenHelper testTokenHelper;

  private static final String BASE_PATH = Resources.ROOT_PATH + "/global";

  /**
   * 测试获取系统配置 - 无需认证（@PermitAll）
   */
  @Test
  void testGetProfileWithoutAuth() {
    given()
      .when()
      .get(BASE_PATH + "/profile")
      .then()
      .statusCode(200)
      .body("settings", notNullValue())
      .body("apiRootPath", notNullValue())
      .body("storageProvider", notNullValue());
  }

  /**
   * 测试获取系统配置 - 带认证
   */
  @Test
  void testGetProfileWithAuth() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .get(BASE_PATH + "/profile")
      .then()
      .statusCode(200)
      .body("settings", notNullValue())
      .body("apiRootPath", notNullValue())
      .body("storageProvider", notNullValue());
  }

  /**
   * 测试系统配置中包含正确的 apiRootPath
   */
  @Test
  void testProfileContainsApiRootPath() {
    given()
      .when()
      .get(BASE_PATH + "/profile")
      .then()
      .statusCode(200)
      .body("apiRootPath", equalTo("/api"));
  }

  /**
   * 测试系统配置中包含 storageProvider 信息
   */
  @Test
  void testProfileContainsStorageProvider() {
    given()
      .when()
      .get(BASE_PATH + "/profile")
      .then()
      .statusCode(200)
      .body("storageProvider.type", notNullValue());
  }

  /**
   * 测试系统配置中包含 settings 信息
   */
  @Test
  void testProfileContainsSettings() {
    given()
      .when()
      .get(BASE_PATH + "/profile")
      .then()
      .statusCode(200)
      .body("settings.appName", notNullValue());
  }

  /**
   * 测试系统配置中包含 projectBaseDomain
   */
  @Test
  void testProfileContainsProjectBaseDomain() {
    given()
      .when()
      .get(BASE_PATH + "/profile")
      .then()
      .statusCode(200)
      .body("projectBaseDomain", notNullValue());
  }

  /**
   * 测试系统配置中包含 routingMode
   */
  @Test
  void testProfileContainsRoutingMode() {
    given()
      .when()
      .get(BASE_PATH + "/profile")
      .then()
      .statusCode(200)
      .body("routingMode", equalTo("path"));
  }

  /**
   * 测试 path 模式（localhost）下 edgeUrlTemplate 为经 Java 代理的相对路径
   */
  @Test
  void testPathModeEdgeUrlTemplate() {
    given()
      .when()
      .get(BASE_PATH + "/profile")
      .then()
      .statusCode(200)
      .body("edgeUrlTemplate", equalTo("/api/open/{{projectId}}/functions/{{name}}/invoke"));
  }

  /**
   * 测试 path 模式下 pagesUrlTemplate 为相对路径
   */
  @Test
  void testPathModePagesUrlTemplate() {
    given()
      .when()
      .get(BASE_PATH + "/profile")
      .then()
      .statusCode(200)
      .body("pagesUrlTemplate", equalTo("/pages/{{projectId}}"));
  }
}
