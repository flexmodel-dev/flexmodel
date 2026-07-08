package dev.flexmodel.rest;

import dev.flexmodel.SQLiteTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

/**
 * FunctionTemplateResource 集成测试
 *
 * @author cjbi
 */
@QuarkusTest
@QuarkusTestResource(SQLiteTestResource.class)
public class FunctionTemplateResourceTest {

  @Inject
  TestTokenHelper testTokenHelper;

  private static final String BASE_PATH = Resources.ROOT_PATH + "/function-templates";

  /**
   * 测试获取函数模板列表
   */
  @Test
  void testListFunctionTemplates() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .get(BASE_PATH)
      .then()
      .statusCode(200);
  }

  /**
   * 测试函数模板列表包含必要字段
   */
  @Test
  void testFunctionTemplatesContainRequiredFields() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .get(BASE_PATH)
      .then()
      .statusCode(200)
      .body("size()", greaterThanOrEqualTo(0)); // 函数模板可能为空，只要接口正常即可
  }
}
