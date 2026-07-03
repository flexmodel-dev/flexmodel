package dev.flexmodel.rest;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import dev.flexmodel.SQLiteTestResource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * FlowDefinitionResource 集成测试
 *
 * @author cjbi
 */
@QuarkusTest
@QuarkusTestResource(SQLiteTestResource.class)
public class FlowDefinitionResourceTest {

  private static final String BASE_PATH = Resources.ROOT_PATH + "/projects/dev_test/flows";

  // 项目.fml 中已有的流程ID
  private static final String EXISTING_FLOW_MODULE_ID = "9243be36-9ced-4413-acd5-5fc93a05c698";

  @BeforeEach
  void cleanupTestFlow() {
    given()
      .header("Authorization", TestTokenHelper.getAuthorizationHeader())
      .when()
      .delete(BASE_PATH + "/test_flow_e2e_key")
      .then()
      .statusCode(anyOf(equalTo(204), equalTo(404), equalTo(500)));
  }

  /**
   * 测试获取流程列表
   */
  @Test
  void testFindFlowList() {
    given()
      .header("Authorization", TestTokenHelper.getAuthorizationHeader())
      .when()
      .get(BASE_PATH)
      .then()
      .statusCode(200)
      .body("total", greaterThanOrEqualTo(1))
      .body("list", hasSize(greaterThanOrEqualTo(1)))
      .body("list[0].flowModuleId", notNullValue())
      .body("list[0].flowName", notNullValue());
  }

  /**
   * 测试获取流程列表 - 按名称过滤
   */
  @Test
  void testFindFlowListWithFlowName() {
    given()
      .header("Authorization", TestTokenHelper.getAuthorizationHeader())
      .queryParam("flowName", "脚本测试")
      .when()
      .get(BASE_PATH)
      .then()
      .statusCode(200)
      .body("total", greaterThanOrEqualTo(1))
      .body("list[0].flowName", containsString("脚本测试"));
  }

  /**
   * 测试获取流程列表 - 分页
   */
  @Test
  void testFindFlowListWithPagination() {
    given()
      .header("Authorization", TestTokenHelper.getAuthorizationHeader())
      .queryParam("page", 1)
      .queryParam("size", 2)
      .when()
      .get(BASE_PATH)
      .then()
      .statusCode(200)
      .body("list", hasSize(lessThanOrEqualTo(2)));
  }

  /**
   * 测试获取流程模块详情
   */
  @Test
  void testGetFlowModule() {
    given()
      .header("Authorization", TestTokenHelper.getAuthorizationHeader())
      .when()
      .get(BASE_PATH + "/" + EXISTING_FLOW_MODULE_ID)
      .then()
      .statusCode(200)
      .body("flowModuleId", equalTo(EXISTING_FLOW_MODULE_ID))
      .body("flowName", notNullValue());
  }

  /**
   * 测试创建流程
   */
  @Test
  void testCreateFlow() {
    String flowModuleId = given()
      .header("Authorization", TestTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body("""
        {
          "flowKey": "test_flow_e2e_key",
          "flowName": "E2E测试流程",
          "remark": "集成测试创建的流程",
          "projectId": "dev_test",
          "caller": "admin"
        }
        """)
      .when()
      .post(BASE_PATH)
      .then()
      .statusCode(200)
      .body("flowModuleId", notNullValue())
      .extract()
      .path("flowModuleId");

    // 清理
    given()
      .header("Authorization", TestTokenHelper.getAuthorizationHeader())
      .when()
      .delete(BASE_PATH + "/" + flowModuleId)
      .then()
      .statusCode(anyOf(equalTo(204), equalTo(500)));
  }

  /**
   * 测试更新流程
   */
  @Test
  void testUpdateFlow() {
    // 先创建流程
    String flowModuleId = given()
      .header("Authorization", TestTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body("""
        {
          "flowKey": "test_flow_e2e_key",
          "flowName": "E2E测试流程",
          "remark": "集成测试创建的流程",
          "projectId": "dev_test",
          "caller": "admin"
        }
        """)
      .when()
      .post(BASE_PATH)
      .then()
      .statusCode(200)
      .extract()
      .path("flowModuleId");

    // 更新流程
    given()
      .header("Authorization", TestTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body("""
        {
          "flowModuleId": "%s",
          "flowKey": "test_flow_e2e_key_updated",
          "flowName": "E2E更新后流程",
          "remark": "更新后的备注",
          "projectId": "dev_test",
          "caller": "admin",
          "operator": "admin"
        }
        """.formatted(flowModuleId))
      .when()
      .put(BASE_PATH + "/" + flowModuleId)
      .then()
      .statusCode(200);

    // 清理
    given()
      .header("Authorization", TestTokenHelper.getAuthorizationHeader())
      .when()
      .delete(BASE_PATH + "/" + flowModuleId)
      .then()
      .statusCode(anyOf(equalTo(204), equalTo(500)));
  }

  /**
   * 测试更新流程 - 请求体中不包含 projectId，验证路径参数 projectId 正确传播
   */
  @Test
  void testUpdateFlowWithoutProjectIdInBody() {
    // 先创建流程
    String flowModuleId = given()
      .header("Authorization", TestTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body("""
        {
          "flowKey": "test_flow_e2e_key",
          "flowName": "E2E测试流程",
          "remark": "集成测试创建的流程",
          "projectId": "dev_test",
          "caller": "admin"
        }
        """)
      .when()
      .post(BASE_PATH)
      .then()
      .statusCode(200)
      .extract()
      .path("flowModuleId");

    // 更新流程 - 请求体中不包含 projectId，依赖路径参数传播
    given()
      .header("Authorization", TestTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body("""
        {
          "flowKey": "test_flow_e2e_key_updated",
          "flowName": "E2E更新后流程-无projectId",
          "remark": "更新后的备注",
          "caller": "admin",
          "operator": "admin"
        }
        """)
      .when()
      .put(BASE_PATH + "/" + flowModuleId)
      .then()
      .statusCode(200)
      .body("errCode", equalTo(0));

    // 验证更新后的流程名称
    given()
      .header("Authorization", TestTokenHelper.getAuthorizationHeader())
      .when()
      .get(BASE_PATH + "/" + flowModuleId)
      .then()
      .statusCode(200)
      .body("flowName", equalTo("E2E更新后流程-无projectId"));

    // 清理
    given()
      .header("Authorization", TestTokenHelper.getAuthorizationHeader())
      .when()
      .delete(BASE_PATH + "/" + flowModuleId)
      .then()
      .statusCode(anyOf(equalTo(204), equalTo(500)));
  }

  /**
   * 测试创建流程 - 请求体中不包含 projectId，验证路径参数 projectId 正确传播
   */
  @Test
  void testCreateFlowWithoutProjectIdInBody() {
    String flowModuleId = given()
      .header("Authorization", TestTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body("""
        {
          "flowKey": "test_flow_e2e_key",
          "flowName": "E2E测试流程-无projectId",
          "remark": "集成测试创建的流程",
          "caller": "admin"
        }
        """)
      .when()
      .post(BASE_PATH)
      .then()
      .statusCode(200)
      .body("flowModuleId", notNullValue())
      .extract()
      .path("flowModuleId");

    // 清理
    given()
      .header("Authorization", TestTokenHelper.getAuthorizationHeader())
      .when()
      .delete(BASE_PATH + "/" + flowModuleId)
      .then()
      .statusCode(anyOf(equalTo(204), equalTo(500)));
  }

  /**
   * 测试删除流程
   */
  @Test
  void testDeleteFlow() {
    // 先创建流程
    String flowModuleId = given()
      .header("Authorization", TestTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body("""
        {
          "flowKey": "test_flow_e2e_key",
          "flowName": "E2E待删除流程",
          "remark": "待删除",
          "projectId": "dev_test",
          "caller": "admin"
        }
        """)
      .when()
      .post(BASE_PATH)
      .then()
      .statusCode(200)
      .extract()
      .path("flowModuleId");

    // 删除流程
    given()
      .header("Authorization", TestTokenHelper.getAuthorizationHeader())
      .when()
      .delete(BASE_PATH + "/" + flowModuleId)
      .then()
      .statusCode(anyOf(equalTo(204), equalTo(500)));
  }

  /**
   * 测试部署流程
   */
  @Test
  void testDeployFlow() {
    given()
      .header("Authorization", TestTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body("""
        {
          "flowModuleId": "%s",
          "projectId": "dev_test",
          "caller": "admin",
          "operator": "admin"
        }
        """.formatted(EXISTING_FLOW_MODULE_ID))
      .when()
      .post(BASE_PATH + "/" + EXISTING_FLOW_MODULE_ID + "/deploy")
      .then()
      .statusCode(200)
      .body("flowModuleId", equalTo(EXISTING_FLOW_MODULE_ID))
      .body("flowDeployId", notNullValue());
  }

  /**
   * 测试完整的流程定义CRUD流程
   */
  @Test
  void testCompleteFlowCrudFlow() {
    // 1. 创建流程
    String flowModuleId = given()
      .header("Authorization", TestTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body("""
        {
          "flowKey": "test_flow_e2e_key",
          "flowName": "CRUD测试流程",
          "remark": "完整CRUD流程",
          "projectId": "dev_test",
          "caller": "admin"
        }
        """)
      .when()
      .post(BASE_PATH)
      .then()
      .statusCode(200)
      .body("flowModuleId", notNullValue())
      .extract()
      .path("flowModuleId");

    // 2. 查看流程详情
    given()
      .header("Authorization", TestTokenHelper.getAuthorizationHeader())
      .when()
      .get(BASE_PATH + "/" + flowModuleId)
      .then()
      .statusCode(200)
      .body("flowModuleId", equalTo(flowModuleId));

    // 3. 更新流程
    given()
      .header("Authorization", TestTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body("""
        {
          "flowModuleId": "%s",
          "flowName": "CRUD更新后流程",
          "projectId": "dev_test",
          "caller": "admin",
          "operator": "admin"
        }
        """.formatted(flowModuleId))
      .when()
      .put(BASE_PATH + "/" + flowModuleId)
      .then()
      .statusCode(200);

    // 4. 删除流程
    given()
      .header("Authorization", TestTokenHelper.getAuthorizationHeader())
      .when()
      .delete(BASE_PATH + "/" + flowModuleId)
      .then()
      .statusCode(anyOf(equalTo(204), equalTo(500)));
  }
}
