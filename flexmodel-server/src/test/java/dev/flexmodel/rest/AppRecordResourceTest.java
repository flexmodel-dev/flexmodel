package dev.flexmodel.rest;

import dev.flexmodel.SQLiteTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * AppRecordResource 集成测试。
 */
@QuarkusTest
@QuarkusTestResource(SQLiteTestResource.class)
public class AppRecordResourceTest {

  @Inject
  TestTokenHelper testTokenHelper;

  private static final String BASE_PATH = Resources.ROOT_PATH + "/apps/dev_test/forms";
  private static final String TEST_SUFFIX = Long.toHexString(System.nanoTime());

  @Test
  void testRecordCrud() {
    Response form = createForm("""
      {
        "label": "记录表单-%s",
        "name": "record_form_%s",
        "createNameField": true
      }
      """.formatted(TEST_SUFFIX, TEST_SUFFIX), 201);
    String formName = form.body().path("name");
    String recordsPath = BASE_PATH + "/" + formName + "/records";

    try {
      Response created = given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .contentType(ContentType.JSON)
        .body("""
          {
            "name": "测试记录-%s"
          }
          """.formatted(TEST_SUFFIX))
        .when()
        .post(recordsPath)
        .then()
        .statusCode(201)
        .body("name", equalTo("测试记录-" + TEST_SUFFIX))
        .body("id", not(emptyOrNullString()))
        .body("created_by", not(emptyOrNullString()))
        .body("created_at", not(emptyOrNullString()))
        .extract()
        .response();
      String recordId = created.body().path("id");

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .when()
        .param("page", 1)
        .param("size", 20)
        .param("search", "测试记录-" + TEST_SUFFIX)
        .param("sortField", "created_at")
        .param("sortOrder", "DESC")
        .get(recordsPath)
        .then()
        .statusCode(200)
        .body("total", greaterThanOrEqualTo(1))
        .body("list[0].id", equalTo(recordId));

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .when()
        .get(recordsPath + "/" + recordId)
        .then()
        .statusCode(200)
        .body("id", equalTo(recordId))
        .body("name", equalTo("测试记录-" + TEST_SUFFIX));

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .contentType(ContentType.JSON)
        .body("""
          {
            "name": "测试记录更新-%s"
          }
          """.formatted(TEST_SUFFIX))
        .when()
        .put(recordsPath + "/" + recordId)
        .then()
        .statusCode(200)
        .body("id", equalTo(recordId))
        .body("name", equalTo("测试记录更新-" + TEST_SUFFIX));

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .contentType(ContentType.JSON)
        .body("""
          {
            "not_exists": "value"
          }
          """)
        .when()
        .post(recordsPath)
        .then()
        .statusCode(400);

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .contentType(ContentType.JSON)
        .body("""
          {
            "id": "custom-id"
          }
          """)
        .when()
        .post(recordsPath)
        .then()
        .statusCode(400);

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .contentType(ContentType.JSON)
        .body("""
          {
            "name": ""
          }
          """)
        .when()
        .post(recordsPath)
        .then()
        .statusCode(400);

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .when()
        .delete(recordsPath + "/" + recordId)
        .then()
        .statusCode(204);

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .when()
        .get(recordsPath + "/" + recordId)
        .then()
        .statusCode(404);
    } finally {
      deleteFormQuietly(formName);
    }
  }

  @Test
  void testFindRecordsUnauthorized() {
    given()
      .when()
      .get(BASE_PATH + "/not_exists/records")
      .then()
      .statusCode(401);
  }

  private Response createForm(String body, int expectedStatus) {
    Response response = given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body(body)
      .when()
      .post(BASE_PATH);
    response.then().statusCode(expectedStatus);
    return response;
  }

  private void deleteFormQuietly(String formName) {
    if (formName == null) {
      return;
    }
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .delete(BASE_PATH + "/" + formName)
      .then()
      .statusCode(anyOf(equalTo(204), equalTo(404), equalTo(400)));
  }
}