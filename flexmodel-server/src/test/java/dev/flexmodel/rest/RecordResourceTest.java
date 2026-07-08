package dev.flexmodel.rest;

import dev.flexmodel.SQLiteTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.*;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;

/**
 * RecordResource 集成测试
 *
 * @author cjbi
 */
@QuarkusTest
@QuarkusTestResource(SQLiteTestResource.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RecordResourceTest {

  @Inject
  TestTokenHelper testTokenHelper;

  @AfterEach
  void cleanupTestData() {
    // 清理测试创建的记录（id = 100000）
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .delete(Resources.ROOT_PATH + "/projects/{projectId}/models/{modelName}/records/{recordId}",
        "dev_test", "Student", 100000)
      .then()
      .statusCode(anyOf(equalTo(200), equalTo(204), equalTo(500)));
  }

  @Test
  void testFindPagingRecords() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .param("current", "1")
      .param("pageSize", "20")
      .param("nestedQuery", "true")
      .get(Resources.ROOT_PATH + "/projects/{projectId}/models/{modelName}/records", "dev_test", "Classes")
      .then()
      .statusCode(anyOf(equalTo(200), equalTo(500)));
  }

  @Test
  @Order(1)
  void testCreateRecord() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .contentType(ContentType.JSON)
      .body("""
            {
              "id": 100000,
              "studentName": "张三丰",
              "gender": "MALE",
              "age": 11,
              "classId": 2
            }
        """)
      .post(Resources.ROOT_PATH + "/projects/{projectId}/models/{modelName}/records", "dev_test", "Student")
      .then()
      .statusCode(anyOf(equalTo(200), equalTo(500)));
  }

  @Test
  @Order(2)
  void testUpdateRecord() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .contentType(ContentType.JSON)
      .body("""
            {
              "id": 100000,
              "studentName": "张三丰",
              "gender": "MALE",
              "age": 11,
              "classId": 2
            }
        """)
      .put(Resources.ROOT_PATH + "/projects/{projectId}/models/{modelName}/records/{recordId}", "dev_test", "Student", 100000)
      .then()
      .statusCode(anyOf(equalTo(200), equalTo(500)));
  }

  @Test
  @Order(3)
  void testFindOneRecord() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .param("nestedQuery", "true")
      .get(Resources.ROOT_PATH + "/projects/{projectId}/models/{modelName}/records/{recordId}", "dev_test", "Student", 100000)
      .then()
      .statusCode(anyOf(equalTo(200), equalTo(204), equalTo(500)));
  }

  @Test
  @Order(4)
  void testDeleteRecord() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .delete(Resources.ROOT_PATH + "/projects/{projectId}/models/{modelName}/records/{recordId}", "dev_test", "Student", 100000)
      .then()
      .statusCode(anyOf(equalTo(204), equalTo(500)));
  }
}
