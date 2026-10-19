package dev.flexmodel.rest;

import dev.flexmodel.SQLiteTestResource;
import dev.flexmodel.auth.service.RoleService;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@QuarkusTestResource(SQLiteTestResource.class)
public class SystemRecordResourceTest {

  private static final String BASE_PATH = Resources.ROOT_PATH + "/system/forms";
  private static final String TEST_SUFFIX = Long.toHexString(System.nanoTime());

  @Inject
  TestTokenHelper testTokenHelper;

  @Inject
  RoleService roleService;

  @Test
  void testUserDepartmentAndRoleRelations() {
    String departmentId = createDepartment("测试部门-" + TEST_SUFFIX);
    Response created = given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body("""
        {
          "name": "系统用户-%s",
          "email": "system-user-%s@example.com",
          "password": "123456",
          "status": "ACTIVE",
          "manager_user_id": null,
          "roleIds": ["admin"],
          "departments": [
            {"departmentId": "%s", "isMain": true, "jobTitle": "工程师"}
          ]
        }
        """.formatted(TEST_SUFFIX, TEST_SUFFIX, departmentId))
      .when()
      .post(BASE_PATH + "/f_user/records")
      .then()
      .statusCode(201)
      .body("id", not(emptyOrNullString()))
      .body("password_hash", nullValue())
      .body("roleIds", hasItem("admin"))
      .body("departments.departmentId", hasItem(departmentId))
      .body("departments.jobTitle", hasItem("工程师"))
      .extract()
      .response();
    String userId = created.body().path("id");

    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body("""
        {
          "roleIds": [],
          "departments": []
        }
        """)
      .when()
      .put(BASE_PATH + "/f_user/records/" + userId)
      .then()
      .statusCode(200)
      .body("roleIds", hasSize(0))
      .body("departments", hasSize(0));

    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .delete(BASE_PATH + "/f_user/records/" + userId)
      .then()
      .statusCode(204);
  }

  @Test
  void testUserRequiresExactlyOneMainDepartment() {
    String firstDepartmentId = createDepartment("主部门一-" + TEST_SUFFIX);
    String secondDepartmentId = createDepartment("主部门二-" + TEST_SUFFIX);
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body("""
        {
          "name": "多主部门用户-%s",
          "password": "123456",
          "status": "ACTIVE",
          "roleIds": [],
          "departments": [
            {"departmentId": "%s", "isMain": true, "jobTitle": ""},
            {"departmentId": "%s", "isMain": true, "jobTitle": ""}
          ]
        }
        """.formatted(TEST_SUFFIX, firstDepartmentId, secondDepartmentId))
      .when()
      .post(BASE_PATH + "/f_user/records")
      .then()
      .statusCode(400);
  }

  @Test
  void testDepartmentRejectsSelfReferenceAndCycle() {
    String rootId = createDepartment("循环根-" + TEST_SUFFIX);
    String childId = createDepartment("循环子-" + TEST_SUFFIX, rootId);

    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body("{\"name\": \"循环根更新\", \"parent_id\": \"%s\"}".formatted(rootId))
      .when()
      .put(BASE_PATH + "/f_department/records/" + rootId)
      .then()
      .statusCode(400);

    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body("{\"name\": \"循环根更新\", \"parent_id\": \"%s\"}".formatted(childId))
      .when()
      .put(BASE_PATH + "/f_department/records/" + rootId)
      .then()
      .statusCode(400);
  }

  @Test
  void testRoleResourceRelationsAndPermissionCalculation() {
    Response created = given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body("""
        {
          "name": "测试角色-%s",
          "description": "系统表单角色",
          "resourceIds": [1]
        }
        """.formatted(TEST_SUFFIX))
      .when()
      .post(BASE_PATH + "/f_role/records")
      .then()
      .statusCode(201)
      .body("resourceIds", hasItem(1))
      .body("resources.permission", hasItem("*"))
      .extract()
      .response();
    String roleId = created.body().path("id");

    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body("""
        {
          "name": "测试角色-%s",
          "description": "系统表单角色",
          "resourceIds": [100]
        }
        """.formatted(TEST_SUFFIX))
      .when()
      .put(BASE_PATH + "/f_role/records/" + roleId)
      .then()
      .statusCode(200)
      .body("resourceIds", hasItem(100))
      .body("resourceIds", not(hasItem(1)))
      .body("resources.permission", hasItem("project:*"));

    assertTrue(roleService.findPermissions(List.of(roleId)).contains("project:*"));

    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .delete(BASE_PATH + "/f_role/records/" + roleId)
      .then()
      .statusCode(204);
  }

  private String createDepartment(String name) {
    return createDepartment(name, null);
  }

  private String createDepartment(String name, String parentId) {
    Response response = given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body(new io.vertx.core.json.JsonObject()
        .put("name", name)
        .put("parent_id", parentId)
        .encode())
      .when()
      .post(BASE_PATH + "/f_department/records")
      .then()
      .statusCode(201)
      .body("id", not(emptyOrNullString()))
      .extract()
      .response();
    return response.body().path("id");
  }
}
