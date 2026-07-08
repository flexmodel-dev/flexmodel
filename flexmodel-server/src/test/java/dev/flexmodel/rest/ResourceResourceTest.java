package dev.flexmodel.rest;

import dev.flexmodel.SQLiteTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * ResourceResource 集成测试（权限资源管理）
 *
 * @author cjbi
 */
@QuarkusTest
@QuarkusTestResource(SQLiteTestResource.class)
public class ResourceResourceTest {

  @Inject
  TestTokenHelper testTokenHelper;

  private static final String BASE_PATH = Resources.ROOT_PATH + "/resources";

  /**
   * 测试获取资源列表
   */
  @Test
  void testFindAllResources() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .get(BASE_PATH)
      .then()
      .statusCode(200)
      .body("size()", greaterThanOrEqualTo(1));
  }

  /**
   * 测试资源列表包含必要字段
   */
  @Test
  void testResourcesContainRequiredFields() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .get(BASE_PATH)
      .then()
      .statusCode(200)
      .body("[0].id", notNullValue())
      .body("[0].name", notNullValue())
      .body("[0].permission", notNullValue());
  }

  /**
   * 测试获取资源树
   */
  @Test
  void testFindResourceTree() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .get(BASE_PATH + "/tree")
      .then()
      .statusCode(200)
      .body("size()", greaterThanOrEqualTo(1));
  }

  /**
   * 测试资源树包含层级结构
   */
  @Test
  void testResourceTreeContainsHierarchy() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .get(BASE_PATH + "/tree")
      .then()
      .statusCode(200)
      .body("[0].id", notNullValue())
      .body("[0].name", notNullValue());
  }
}
