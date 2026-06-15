package dev.flexmodel.rest;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import dev.flexmodel.SQLiteTestResource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * GlobalResource 集成测试
 *
 * @author cjbi
 */
@QuarkusTest
@QuarkusTestResource(SQLiteTestResource.class)
public class GlobalResourceTest {

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
      .header("Authorization", TestTokenHelper.getAuthorizationHeader())
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
}
