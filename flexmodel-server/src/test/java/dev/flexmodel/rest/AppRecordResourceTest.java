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
        "createDisplayField": true
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
  void testFindRecordsByViewTab() {
    Response form = createForm("""
      {
        "label": "页签记录表单-%s",
        "name": "view_record_form_%s",
        "createDisplayField": true
      }
      """.formatted(TEST_SUFFIX, TEST_SUFFIX), 201);
    String formName = form.body().path("name");
    String recordsPath = BASE_PATH + "/" + formName + "/records";

    try {
      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .contentType(ContentType.JSON)
        .body("""
          {
            "viewTabs": [
              {
                "id": "zhang",
                "label": "张姓",
                "filter": {
                  "_and": [
                    {"name": {"_contains": "张"}}
                  ]
                }
              }
            ]
          }
          """)
        .when()
        .patch(BASE_PATH + "/" + formName)
        .then()
        .statusCode(200);

      createRecord(recordsPath, "{\"name\": \"张三\"}", 201);
      createRecord(recordsPath, "{\"name\": \"李四\"}", 201);

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .when()
        .param("page", 1)
        .param("size", 20)
        .param("viewTabId", "zhang")
        .get(recordsPath)
        .then()
        .statusCode(200)
        .body("total", equalTo(1))
        .body("list[0].name", equalTo("张三"));

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .when()
        .param("page", 1)
        .param("size", 20)
        .param("viewTabId", "all")
        .get(recordsPath)
        .then()
        .statusCode(200)
        .body("total", greaterThanOrEqualTo(2));

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .when()
        .param("page", 1)
        .param("size", 20)
        .param("viewTabId", "unknown")
        .get(recordsPath)
        .then()
        .statusCode(400);
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

  @Test
  void testReferenceValueRendering() {
    String targetName = "reference_target_" + TEST_SUFFIX;
    String sourceName = "reference_source_" + TEST_SUFFIX;
    createForm("""
      {
        "label": "引用目标表单-%s",
        "name": "%s",
        "createDisplayField": true
      }
      """.formatted(TEST_SUFFIX, targetName), 201);
    createForm("""
      {
        "label": "引用来源表单-%s",
        "name": "%s",
        "createDisplayField": true
      }
      """.formatted(TEST_SUFFIX, sourceName), 201);
    Response referenceField = createField(sourceName, """
      {
        "label": "引用目标",
        "type": "REFERENCE",
        "required": false,
        "referenceForm": "%s"
      }
      """.formatted(targetName), 201);
    String fieldName = referenceField.body().path("name");
    String targetRecordsPath = BASE_PATH + "/" + targetName + "/records";
    String sourceRecordsPath = BASE_PATH + "/" + sourceName + "/records";

    try {
      Response targetRecord = given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .contentType(ContentType.JSON)
        .body("""
          {"name": "引用目标记录-%s"}
          """.formatted(TEST_SUFFIX))
        .when()
        .post(targetRecordsPath)
        .then()
        .statusCode(201)
        .extract()
        .response();
      String targetRecordId = targetRecord.body().path("id");

      Response sourceRecord = given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .contentType(ContentType.JSON)
        .body("""
          {
            "name": "引用来源记录-%s",
            "%s": "%s"
          }
          """.formatted(TEST_SUFFIX, fieldName, targetRecordId))
        .when()
        .post(sourceRecordsPath)
        .then()
        .statusCode(201)
        .extract()
        .response();
      String sourceRecordId = sourceRecord.body().path("id");

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .when()
        .get(sourceRecordsPath)
        .then()
        .statusCode(200)
        .body("list[0]." + fieldName + ".id", equalTo(targetRecordId))
        .body("list[0]." + fieldName + ".displayValue", equalTo("引用目标记录-" + TEST_SUFFIX))
        .body("list[0]." + fieldName + ".referenceForm", equalTo(targetName));

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .when()
        .get(sourceRecordsPath + "/" + sourceRecordId)
        .then()
        .statusCode(200)
        .body(fieldName + ".id", equalTo(targetRecordId))
        .body(fieldName + ".displayValue", equalTo("引用目标记录-" + TEST_SUFFIX))
        .body(fieldName + ".referenceForm", equalTo(targetName));
    } finally {
      deleteFormQuietly(sourceName);
      deleteFormQuietly(targetName);
    }
  }

  @Test
  void testSubformRecordContext() {
    String mainName = "subform_record_main_" + TEST_SUFFIX;
    String childName = "subform_record_child_" + TEST_SUFFIX;
    Response main = createForm("""
      {
        "label": "主记录表单-%s",
        "name": "%s",
        "description": "主记录表单",
        "createDisplayField": true
      }
      """.formatted(TEST_SUFFIX, mainName), 201);
    Response child = createForm("""
      {
        "label": "子记录表单-%s",
        "name": "%s",
        "description": "子记录表单",
        "createDisplayField": true
      }
      """.formatted(TEST_SUFFIX, childName), 201);
    Response reference = createField(childName, """
      {
        "label": "主表单引用",
        "type": "REFERENCE",
        "referenceForm": "%s",
        "required": false
      }
      """.formatted(mainName), 201);
    String referenceField = reference.body().path("name");
    String mainRecordsPath = BASE_PATH + "/" + mainName + "/records";
    String childRecordsPath = BASE_PATH + "/" + childName + "/records";

    try {
      Response mainRecord = given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .contentType(ContentType.JSON)
        .body("""
          {"name": "主记录-%s"}
          """.formatted(TEST_SUFFIX))
        .when()
        .post(mainRecordsPath)
        .then()
        .statusCode(201)
        .extract()
        .response();
      String mainRecordId = mainRecord.body().path("id");

      Response childRecord = given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .contentType(ContentType.JSON)
        .body("""
          {"name": "子记录-%s"}
          """.formatted(TEST_SUFFIX))
        .when()
        .post(childRecordsPath + "?mainFormName=" + mainName + "&mainRecordId=" + mainRecordId
          + "&referenceField=" + referenceField)
        .then()
        .statusCode(201)
        .body(referenceField + ".id", equalTo(mainRecordId))
        .extract()
        .response();
      String childRecordId = childRecord.body().path("id");

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .contentType(ContentType.JSON)
        .body("""
          {"name": "未关联子记录-%s"}
          """.formatted(TEST_SUFFIX))
        .when()
        .post(childRecordsPath)
        .then()
        .statusCode(201);

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .when()
        .get(childRecordsPath + "?mainFormName=" + mainName + "&mainRecordId=" + mainRecordId
          + "&referenceField=" + referenceField)
        .then()
        .statusCode(200)
        .body("total", equalTo(1))
        .body("list[0].id", equalTo(childRecordId));

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .contentType(ContentType.JSON)
        .body("""
          {
            "name": "子记录-%s",
            "%s": "not-main-record"
          }
          """.formatted(TEST_SUFFIX, referenceField))
        .when()
        .post(childRecordsPath + "?mainFormName=" + mainName + "&mainRecordId=" + mainRecordId
          + "&referenceField=" + referenceField)
        .then()
        .statusCode(400);

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .when()
        .get(childRecordsPath + "/" + childRecordId + "?mainFormName=" + mainName
          + "&mainRecordId=not-main-record&referenceField=" + referenceField)
        .then()
        .statusCode(404);

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .contentType(ContentType.JSON)
        .body("""
          {"name": "子记录更新-%s"}
          """.formatted(TEST_SUFFIX))
        .when()
        .put(childRecordsPath + "/" + childRecordId + "?mainFormName=" + mainName
          + "&mainRecordId=" + mainRecordId + "&referenceField=" + referenceField)
        .then()
        .statusCode(200)
        .body("name", equalTo("子记录更新-" + TEST_SUFFIX));

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .when()
        .delete(childRecordsPath + "/" + childRecordId + "?mainFormName=" + mainName
          + "&mainRecordId=not-main-record&referenceField=" + referenceField)
        .then()
        .statusCode(404);

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .when()
        .delete(childRecordsPath + "/" + childRecordId + "?mainFormName=" + mainName
          + "&mainRecordId=" + mainRecordId + "&referenceField=" + referenceField)
        .then()
        .statusCode(204);
    } finally {
      deleteFormQuietly(childName);
      deleteFormQuietly(mainName);
    }
  }

  @Test
  void testSubformRecordContextAllowsAnyBusinessField() {
    String mainName = "subform_text_main_" + TEST_SUFFIX;
    String childName = "subform_text_child_" + TEST_SUFFIX;
    createForm("""
      {
        "label": "Text main form",
        "name": "%s",
        "description": "Main form",
        "createDisplayField": true
      }
      """.formatted(mainName), 201);
    createForm("""
      {
        "label": "Text child form",
        "name": "%s",
        "description": "Child form",
        "createDisplayField": true
      }
      """.formatted(childName), 201);
    Response linkField = createField(childName, """
      {
        "label": "Main record link",
        "type": "TEXT",
        "required": false
      }
      """, 201);
    String fieldName = linkField.body().path("name");
    String mainRecordsPath = BASE_PATH + "/" + mainName + "/records";
    String childRecordsPath = BASE_PATH + "/" + childName + "/records";

    try {
      Response mainRecord = given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .contentType(ContentType.JSON)
        .body("""
          {"name": "Main record"}
          """)
        .when()
        .post(mainRecordsPath)
        .then()
        .statusCode(201)
        .extract()
        .response();
      String mainRecordId = mainRecord.body().path("id");

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .contentType(ContentType.JSON)
        .body("""
          {"name": "Child record"}
          """)
        .when()
        .post(childRecordsPath + "?mainFormName=" + mainName + "&mainRecordId=" + mainRecordId
          + "&referenceField=" + fieldName)
        .then()
        .statusCode(201)
        .body(fieldName, equalTo(mainRecordId));

      given()
        .header("Authorization", testTokenHelper.getAuthorizationHeader())
        .when()
        .get(childRecordsPath + "?mainFormName=" + mainName + "&mainRecordId=" + mainRecordId
          + "&referenceField=" + fieldName)
        .then()
        .statusCode(200)
        .body("total", equalTo(1));
    } finally {
      deleteFormQuietly(childName);
      deleteFormQuietly(mainName);
    }
  }

  private Response createForm(String body, int expectedStatus) {
    Response response = given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body(body)
      .when()
      .post(BASE_PATH);
    response.then().statusCode(expectedStatus);
    if (expectedStatus == 201) exposeFixtureFields(response.body().path("name"));
    return response;
  }

  private Response createRecord(String recordsPath, String body, int expectedStatus) {
    Response response = given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body(body)
      .when()
      .post(recordsPath);
    response.then().statusCode(expectedStatus);
    return response;
  }

  private Response createField(String formName, String body, int expectedStatus) {
    Response response = given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .contentType(ContentType.JSON)
      .body(body)
      .when()
      .post(BASE_PATH + "/" + formName + "/fields");
    response.then().statusCode(expectedStatus);
    if (expectedStatus == 201) exposeFixtureFields(formName);
    return response;
  }

  /** Record APIs project the form layout; fixtures must place the fields they assert. */
  private void exposeFixtureFields(String formName) {
    java.util.List<String> names = given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .get(BASE_PATH + "/" + formName).then().statusCode(200).extract().path("fields.name");
    var items = names.stream().map(name -> java.util.Map.of("type", "FIELD", "name", name)).toList();
    given().header("Authorization", testTokenHelper.getAuthorizationHeader()).contentType(ContentType.JSON)
      .body(java.util.Map.of("items", items)).put(BASE_PATH + "/" + formName + "/layout").then().statusCode(200);
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
