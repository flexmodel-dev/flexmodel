package dev.flexmodel.rest;

import dev.flexmodel.SQLiteTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.not;

/**
 * 权限过滤器（PermissionFilter）端到端集成测试。
 * <p>
 * 通过 {@code X-Test-Permissions} 请求头注入模拟权限集，
 * 验证 {@code @RequiresPermissions} 与 {@code requirePermission} 的全链路行为。
 */
@QuarkusTest
@QuarkusTestResource(SQLiteTestResource.class)
class PermissionFilterTest {

  @Inject
  TestTokenHelper testTokenHelper;

  // ── 系统 JWT (permissions=null) → 全部放行 ──

  @Test
  void systemJwtShouldPassGlobalModeling() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .get(Resources.ROOT_PATH + "/projects/dev_test/models")
      .then()
      .statusCode(200);
  }

  @Test
  void systemJwtShouldPassRecordList() {
    // dev_test 下应该有导入的 FML 模型
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .get(Resources.ROOT_PATH + "/projects/dev_test/models/Student/records")
      .then()
      .statusCode(200);
  }

  // ── 全局通配 * ──

  @Test
  void globalWildcardShouldPassModeling() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .header("X-Test-Permissions", "*")
      .when()
      .get(Resources.ROOT_PATH + "/projects/dev_test/models")
      .then()
      .statusCode(200);
  }

  @Test
  void globalWildcardShouldPassGraphQL() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .header("X-Test-Permissions", "*")
      .contentType(ContentType.JSON)
      .body("{\"query\": \"{ __typename }\"}")
      .when()
      .post(Resources.ROOT_PATH + "/projects/dev_test/graphql")
      .then()
      .statusCode(200);
  }

  // ── 权限串匹配 → 通过 ──

  @Test
  void modelingWildcardShouldPassModelingList() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .header("X-Test-Permissions", "modeling:*")
      .when()
      .get(Resources.ROOT_PATH + "/projects/dev_test/models")
      .then()
      .statusCode(200);
  }

  @Test
  void perModelExactShouldPassModelDetail() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .header("X-Test-Permissions", "modeling:Student:view")
      .when()
      .get(Resources.ROOT_PATH + "/projects/dev_test/models/Student")
      .then()
      .statusCode(200);
  }

  @Test
  void perModelWildcardShouldPassModelDetail() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .header("X-Test-Permissions", "modeling:Student:*")
      .when()
      .get(Resources.ROOT_PATH + "/projects/dev_test/models/Student")
      .then()
      .statusCode(200);
  }

  @Test
  void dataWildcardShouldPassRecordList() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .header("X-Test-Permissions", "data:*")
      .when()
      .get(Resources.ROOT_PATH + "/projects/dev_test/models/Student/records")
      .then()
      .statusCode(200);
  }

  @Test
  void perModelDataShouldPassRecordList() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .header("X-Test-Permissions", "data:Student:view")
      .when()
      .get(Resources.ROOT_PATH + "/projects/dev_test/models/Student/records")
      .then()
      .statusCode(200);
  }

  @Test
  void graphqlExecuteShouldPass() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .header("X-Test-Permissions", "graphql:execute")
      .contentType(ContentType.JSON)
      .body("{\"query\": \"{ __typename }\"}")
      .when()
      .post(Resources.ROOT_PATH + "/projects/dev_test/graphql")
      .then()
      .statusCode(200);
  }

  @Test
  void flowViewShouldPass() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .header("X-Test-Permissions", "flow:view")
      .when()
      .get(Resources.ROOT_PATH + "/projects/dev_test/flows")
      .then()
      .statusCode(200);
  }

  @Test
  void schedulingViewShouldPass() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .header("X-Test-Permissions", "scheduling:view")
      .when()
      .get(Resources.ROOT_PATH + "/projects/dev_test/triggers")
      .then()
      .statusCode(200);
  }

  @Test
  void functionViewShouldPass() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .header("X-Test-Permissions", "function:view")
      .when()
      .get(Resources.ROOT_PATH + "/projects/dev_test/functions")
      .then()
      .statusCode(200);
  }

  // ── 权限不匹配 → 403 ──

  @Test
  void noPermissionDenyModeling() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .header("X-Test-Permissions", "data:Student:view")
      .when()
      .get(Resources.ROOT_PATH + "/projects/dev_test/models")
      .then()
      .statusCode(403);
  }

  @Test
  void wrongModelDataSourceDeny() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .header("X-Test-Permissions", "data:Course:view")
      .when()
      .get(Resources.ROOT_PATH + "/projects/dev_test/models/Student/records")
      .then()
      .statusCode(403);
  }

  @Test
  void viewOnlyDenyWrite() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .header("X-Test-Permissions", "modeling:Student:view")
      .contentType(ContentType.JSON)
      .body("{\"type\":\"entity\",\"name\":\"Student\"}")
      .when()
      .put(Resources.ROOT_PATH + "/projects/dev_test/models/Student")
      .then()
      .statusCode(403);
  }

  @Test
  void schedulingViewDenyExecute() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .header("X-Test-Permissions", "scheduling:view")
      .when()
      .post(Resources.ROOT_PATH + "/projects/dev_test/triggers/NonExistent/execute")
      .then()
      .statusCode(403);
  }

  @Test
  void flowViewDenyExecute() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .header("X-Test-Permissions", "flow:view")
      .contentType(ContentType.JSON)
      .body("{\"flowModuleId\": \"test\"}")
      .when()
      .post(Resources.ROOT_PATH + "/projects/dev_test/flows/instances/start")
      .then()
      .statusCode(403);
  }

  @Test
  void graphqlMissingPermDeny() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .header("X-Test-Permissions", "modeling:*")
      .contentType(ContentType.JSON)
      .body("{\"query\": \"{ __typename }\"}")
      .when()
      .post(Resources.ROOT_PATH + "/projects/dev_test/graphql")
      .then()
      .statusCode(403);
  }

  @Test
  void functionViewDenyInvoke() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .header("X-Test-Permissions", "function:view")
      .contentType(ContentType.JSON)
      .body("{}")
      .when()
      .post(Resources.ROOT_PATH + "/projects/dev_test/functions/FakeFunc/invoke")
      .then()
      .statusCode(403);
  }

  @Test
  void emptyPermissionsDeny() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .header("X-Test-Permissions", "")
      .when()
      .get(Resources.ROOT_PATH + "/projects/dev_test/models")
      .then()
      .statusCode(403);
  }

  @Test
  void mixedPermissionsPassIfOneMatches() {
    // perModel:Student:view 只给了 Student，但 findModels 需要 modeling:view (全局)
    // 这应该 403——perModel 不隐含全局
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .header("X-Test-Permissions", "modeling:Student:view,data:Course:create")
      .when()
      .get(Resources.ROOT_PATH + "/projects/dev_test/models")
      .then()
      .statusCode(403);
  }

  @Test
  void deletePermissionDeniesViewOnSameModel() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .header("X-Test-Permissions", "modeling:Student:delete")
      .when()
      .get(Resources.ROOT_PATH + "/projects/dev_test/models/Student")
      .then()
      .statusCode(403);
  }

  // ── 写操作授权 (200 / 非403) ──

  @Test
  void modelingExecuteShouldPassCreateModel() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .header("X-Test-Permissions", "modeling:*")
      .contentType(ContentType.JSON)
      .body("{\"type\":\"entity\",\"name\":\"PermTestModel\"}")
      .when()
      .post(Resources.ROOT_PATH + "/projects/dev_test/models")
      .then()
      .statusCode(not(403));
  }

  @Test
  void modelingExecuteShouldPassUpdateModel() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .header("X-Test-Permissions", "modeling:Student:*")
      .contentType(ContentType.JSON)
      .body("{\"type\":\"entity\",\"name\":\"Student\"}")
      .when()
      .put(Resources.ROOT_PATH + "/projects/dev_test/models/Student")
      .then()
      .statusCode(not(403));
  }

  @Test
  void modelingExecuteShouldPassExecuteFml() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .header("X-Test-Permissions", "modeling:create")
      .contentType(ContentType.JSON)
      .body("{\"fml\":\"entity TestFml { }\"}")
      .when()
      .post(Resources.ROOT_PATH + "/projects/dev_test/models/fml/execute")
      .then()
      .statusCode(not(403));
  }

  @Test
  void dataCreateShouldPass() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .header("X-Test-Permissions", "data:Student:create")
      .contentType(ContentType.JSON)
      .body("{\"name\":\"test\"}")
      .when()
      .post(Resources.ROOT_PATH + "/projects/dev_test/models/Student/records")
      .then()
      .statusCode(not(403));
  }

  @Test
  void dataUpdateShouldPass() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .header("X-Test-Permissions", "data:Student:update")
      .contentType(ContentType.JSON)
      .body("{\"name\":\"test\"}")
      .when()
      .put(Resources.ROOT_PATH + "/projects/dev_test/models/Student/records/1")
      .then()
      .statusCode(not(403));
  }

  @Test
  void dataDeleteShouldPass() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .header("X-Test-Permissions", "data:Student:delete")
      .when()
      .delete(Resources.ROOT_PATH + "/projects/dev_test/models/Student/records/1")
      .then()
      .statusCode(not(403));
  }

  @Test
  void flowExecuteShouldPassCreateFlow() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .header("X-Test-Permissions", "flow:execute")
      .contentType(ContentType.JSON)
      .body("{\"name\":\"TestFlow\"}")
      .when()
      .post(Resources.ROOT_PATH + "/projects/dev_test/flows")
      .then()
      .statusCode(not(403));
  }

  @Test
  void flowExecuteShouldPassStartProcess() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .header("X-Test-Permissions", "flow:execute")
      .contentType(ContentType.JSON)
      .body("{\"flowModuleId\":\"test\"}")
      .when()
      .post(Resources.ROOT_PATH + "/projects/dev_test/flows/instances/start")
      .then()
      .statusCode(not(403));
  }

  @Test
  void schedulingExecuteShouldPassCreateTrigger() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .header("X-Test-Permissions", "scheduling:execute")
      .contentType(ContentType.JSON)
      .body("{\"name\":\"TestTrigger\"}")
      .when()
      .post(Resources.ROOT_PATH + "/projects/dev_test/triggers")
      .then()
      .statusCode(not(403));
  }

  @Test
  void schedulingExecuteShouldPassExecuteNow() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .header("X-Test-Permissions", "scheduling:execute")
      .when()
      .post(Resources.ROOT_PATH + "/projects/dev_test/triggers/NonExistent/execute")
      .then()
      .statusCode(not(403));
  }

  @Test
  void functionExecuteShouldPassInvoke() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .header("X-Test-Permissions", "function:execute")
      .contentType(ContentType.JSON)
      .body("{}")
      .when()
      .post(Resources.ROOT_PATH + "/projects/dev_test/functions/FakeFunc/invoke")
      .then()
      .statusCode(not(403));
  }

  // ── 写操作拒绝 (403) ──

  @Test
  void modelingViewDenyCreateModel() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .header("X-Test-Permissions", "modeling:view")
      .contentType(ContentType.JSON)
      .body("{\"type\":\"entity\",\"name\":\"DeniedModel\"}")
      .when()
      .post(Resources.ROOT_PATH + "/projects/dev_test/models")
      .then()
      .statusCode(403);
  }

  @Test
  void dataViewDenyCreate() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .header("X-Test-Permissions", "data:Student:view")
      .contentType(ContentType.JSON)
      .body("{\"name\":\"test\"}")
      .when()
      .post(Resources.ROOT_PATH + "/projects/dev_test/models/Student/records")
      .then()
      .statusCode(403);
  }

  @Test
  void dataViewDenyUpdate() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .header("X-Test-Permissions", "data:Student:view")
      .contentType(ContentType.JSON)
      .body("{\"name\":\"test\"}")
      .when()
      .put(Resources.ROOT_PATH + "/projects/dev_test/models/Student/records/1")
      .then()
      .statusCode(403);
  }

  @Test
  void dataViewDenyDelete() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .header("X-Test-Permissions", "data:Student:view")
      .when()
      .delete(Resources.ROOT_PATH + "/projects/dev_test/models/Student/records/1")
      .then()
      .statusCode(403);
  }

  @Test
  void flowViewDenyCreateFlow() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .header("X-Test-Permissions", "flow:view")
      .contentType(ContentType.JSON)
      .body("{\"name\":\"Test\"}")
      .when()
      .post(Resources.ROOT_PATH + "/projects/dev_test/flows")
      .then()
      .statusCode(403);
  }

  @Test
  void schedulingViewDenyCreateTrigger() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .header("X-Test-Permissions", "scheduling:view")
      .contentType(ContentType.JSON)
      .body("{\"name\":\"Test\"}")
      .when()
      .post(Resources.ROOT_PATH + "/projects/dev_test/triggers")
      .then()
      .statusCode(403);
  }

  @Test
  void perModelDataViewDenyGlobalModeling() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .header("X-Test-Permissions", "data:Student:view")
      .when()
      .get(Resources.ROOT_PATH + "/projects/dev_test/models")
      .then()
      .statusCode(403);
  }

  // ── 无注解的资源路径 → 放行 ──

  @Test
  void noAnnotationResourceShouldPass() {
    // AuthProviderConfigResource 没有 @RequiresPermissions，应放行
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .header("X-Test-Permissions", "")  // 空权限集
      .when()
      .get(Resources.ROOT_PATH + "/projects/dev_test/auth-providers")
      .then()
      .statusCode(200);
  }
}
