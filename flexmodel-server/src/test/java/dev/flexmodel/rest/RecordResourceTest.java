package dev.flexmodel.rest;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import dev.flexmodel.SQLiteTestResource;
import dev.flexmodel.common.config.web.jwt.JwtUtil;

import java.time.Duration;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

/**
 * @author cjbi
 */
@QuarkusTest
@QuarkusTestResource(SQLiteTestResource.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RecordResourceTest {

  /**
   * 获取测试用的token
   */
  private String getTestToken() {
    return JwtUtil.sign("admin", Duration.ofMinutes(5));
  }

  @AfterEach
  void cleanupTestData() {
    // 清理测试创建的记录（id = 100000）
    given()
      .header("Authorization", TestTokenHelper.getAuthorizationHeader())
      .when()
      .delete(Resources.ROOT_PATH + "/projects/{projectId}/models/{modelName}/records/{recordId}",
        "dev_test", "Student", 100000);
  }

  @Test
  void testFindPagingRecords() {
    given()
      .header("Authorization", TestTokenHelper.getAuthorizationHeader())
      .when()
      .param("current", "1")
      .param("pageSize", "20")
      .param("nestedQuery", "true")
      .get(Resources.ROOT_PATH + "/projects/{projectId}/models/{modelName}/records", "dev_test", "Classes")
      .then()
      .statusCode(200)
      .body(
        "size()", greaterThanOrEqualTo(1)
      );
  }

  @Test
  @Order(1)
  void testCreateRecord() {
    given()
      .header("Authorization", TestTokenHelper.getAuthorizationHeader())
      .when()
      .contentType(ContentType.JSON)
      .body("""
            {
              "id": 100000,
              "studentName": "张三丰",
              "gender": "MALE",
              "age": 11,
              "classId": 2,
              "studentDetail": {
                "description": "张三丰的描述"
              }
            }
        """)
      .post(Resources.ROOT_PATH + "/projects/{projectId}/models/{modelName}/records", "dev_test", "Student")
      .then()
      .statusCode(200);

    // Verify the record was created
    given()
      .header("Authorization", TestTokenHelper.getAuthorizationHeader())
      .when()
      .param("nestedQuery", "true")
      .get(Resources.ROOT_PATH + "/projects/{projectId}/models/{modelName}/records/{recordId}", "dev_test", "Student", 100000)
      .then()
      .statusCode(200)
      .body(
        "studentName", equalTo("张三丰"),
        "studentDetail.description", equalTo("张三丰的描述")
      );
  }

  @Test
  @Order(2)
  void testUpdateRecord() {
    given()
      .header("Authorization", TestTokenHelper.getAuthorizationHeader())
      .when()
      .contentType(ContentType.JSON)
      .body("""
            {
              "id": 100000,
              "studentName": "张三丰",
              "gender": "MALE",
              "age": 11,
              "classId": 2,
              "studentDetail": {
                "description": "张三丰的描述"
              }
            }
        """)
      .put(Resources.ROOT_PATH + "/projects/{projectId}/models/{modelName}/records/{recordId}", "dev_test", "Student", 100000)
      .then()
      .statusCode(200);
  }

  @Test
  @Order(3)
  void testFindOneRecord() {
    // Create a record first, then find it
    given()
      .header("Authorization", TestTokenHelper.getAuthorizationHeader())
      .when()
      .contentType(ContentType.JSON)
      .body("""
            {
              "id": 100001,
              "studentName": "李四",
              "gender": "MALE",
              "age": 12,
              "classId": 2,
              "studentDetail": {
                "description": "李四的描述"
              }
            }
        """)
      .post(Resources.ROOT_PATH + "/projects/{projectId}/models/{modelName}/records", "dev_test", "Student")
      .then()
      .statusCode(200);

    given()
      .header("Authorization", TestTokenHelper.getAuthorizationHeader())
      .when()
      .param("nestedQuery", "true")
      .get(Resources.ROOT_PATH + "/projects/{projectId}/models/{modelName}/records/{recordId}", "dev_test", "Student", 100001)
      .then()
      .statusCode(200)
      .body(
        "studentName", equalTo("李四"),
        "studentDetail.description", equalTo("李四的描述")
      );

    // Cleanup
    given()
      .header("Authorization", TestTokenHelper.getAuthorizationHeader())
      .when()
      .delete(Resources.ROOT_PATH + "/projects/{projectId}/models/{modelName}/records/{recordId}", "dev_test", "Student", 100001);
  }

  @Test
  @Order(4)
  void testDeleteRecord() {
    given()
      .header("Authorization", TestTokenHelper.getAuthorizationHeader())
      .when()
      .delete(Resources.ROOT_PATH + "/projects/{projectId}/models/{modelName}/records/{recordId}", "dev_test", "Student", 100000)
      .then()
      .statusCode(204);
  }

}
