package dev.flexmodel.rest;

import dev.flexmodel.SQLiteTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@QuarkusTestResource(SQLiteTestResource.class)
class DepartmentResourceTest {

  @Inject
  TestTokenHelper testTokenHelper;

  private static final String BASE_PATH = Resources.ROOT_PATH + "/departments";

  @Test
  void testCreateAndListDepartments() {
    String rootId = given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body("""
        {"name": "总部"}
        """)
      .when()
      .post(BASE_PATH)
      .then()
      .statusCode(200)
      .extract()
      .path("id");

    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body("""
        {"name": "研发部", "parentId": "%s"}
        """.formatted(rootId))
      .when()
      .post(BASE_PATH)
      .then()
      .statusCode(200)
      .body("name", equalTo("研发部"))
      .body("parentId", equalTo(rootId));

    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .get(BASE_PATH)
      .then()
      .statusCode(200)
      .body("size()", greaterThanOrEqualTo(2));

    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .delete(BASE_PATH + "/" + rootId)
      .then()
      .statusCode(400);
  }

  @Test
  void testCreateUserWithDepartmentAndManager() {
    String departmentId = given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body("""
        {"name": "研发中心"}
        """)
      .when()
      .post(BASE_PATH)
      .then()
      .statusCode(200)
      .extract()
      .path("id");

    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body("""
        {
          "name": "部门用户",
          "email": "department-user@test.com",
          "password": "test123",
          "status": "ACTIVE",
          "managerUserId": "admin",
          "roleIds": ["admin"],
          "departments": [
            {
              "departmentId": "%s",
              "isMain": true,
              "jobTitle": "工程师"
            }
          ]
        }
        """.formatted(departmentId))
      .when()
      .post(Resources.ROOT_PATH + "/users")
      .then()
      .statusCode(200)
      .body("departments[0].departmentId", equalTo(departmentId))
      .body("departments[0].isMain", equalTo(true))
      .body("departments[0].jobTitle", equalTo("工程师"))
      .body("managerUserId", equalTo("admin"))
      .body("managerName", equalTo("Admin"));
  }
}
