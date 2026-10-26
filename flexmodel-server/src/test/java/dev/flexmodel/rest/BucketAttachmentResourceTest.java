package dev.flexmodel.rest;

import dev.flexmodel.SQLiteTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(SQLiteTestResource.class)
class BucketAttachmentResourceTest {
  @Inject
  TestTokenHelper tokens;

  @Test
  void attachmentFieldStoresMultipleUrlsBeyondOld512Limit() {
    String formName = "attachment_" + Long.toHexString(System.nanoTime());
    String forms = "/apps/dev_test/forms";
    String form = forms + "/" + formName;
    String auth = tokens.getAuthorizationHeader();
    given().header("Authorization", auth).contentType("application/json")
      .body(Map.of("name", formName, "label", "附件回归", "createDisplayField", false))
      .post(forms).then().statusCode(201);
    try {
      given().header("Authorization", auth).contentType("application/json")
        .body(Map.of("name", "files", "label", "附件", "type", "FILE", "required", false))
        .post(form + "/fields").then().statusCode(201);
      given().header("Authorization", auth).get("/projects/dev_test/models/" + formName)
        .then().statusCode(200).body("fields.find { it.name == 'files' }.length", equalTo(4000));
      given().header("Authorization", auth).contentType("application/json")
        .body(Map.of("items", List.of(Map.of("type", "FIELD", "name", "files"))))
        .put(form + "/layout").then().statusCode(200);
      String urls = IntStream.range(0, 8).mapToObj(index -> "/api/projects/dev_test/buckets/default/objects/dev_test/"
        + formName + "/" + UUID.randomUUID() + "_document_" + index + ".pdf").collect(Collectors.joining(","));
      assertTrue(urls.length() > 512);
      String id = given().header("Authorization", auth).contentType("application/json")
        .body(Map.of("files", urls)).post(form + "/records").then().statusCode(201)
        .body("files", equalTo(urls)).extract().path("id");
      given().header("Authorization", auth).get(form + "/records/" + id)
        .then().statusCode(200).body("files", equalTo(urls));
      given().header("Authorization", auth).contentType("application/json")
        .body(Map.of("label", "业务附件", "required", false)).put(form + "/fields/files")
        .then().statusCode(200).body("type", equalTo("FILE"));
      given().header("Authorization", auth).contentType("application/json")
        .body("{\"files\":null}").put(form + "/records/" + id).then().statusCode(200).body("files", equalTo(null));
    } finally {
      given().header("Authorization", auth).delete(form);
    }
  }

  @Test
  void movesObjectBetweenFolders() {
    String name = "attachment-move-" + UUID.randomUUID();
    String bucket = "/projects/dev_test/buckets/" + name;
    String auth = tokens.getAuthorizationHeader();
    given().header("Authorization", auth).contentType("application/json")
      .body(Map.of("name", name, "visibility", "PRIVATE"))
      .post("/projects/dev_test/buckets").then().statusCode(200);
    try {
      byte[] content = "move me".getBytes(StandardCharsets.UTF_8);
      given().header("Authorization", auth).contentType("application/octet-stream")
        .body(content).put(bucket + "/objects/source/file.txt").then().statusCode(200);

      given().header("Authorization", auth).contentType("application/json")
        .body(Map.of("source", "source/file.txt", "target", "target/file.txt"))
        .post(bucket + "/move").then().statusCode(204);

      given().header("Authorization", auth).get(bucket + "/objects?prefix=source")
        .then().statusCode(200).body("size()", equalTo(0));
      given().header("Authorization", auth).get(bucket + "/objects?prefix=target")
        .then().statusCode(200).body("[0].name", equalTo("file.txt"));

      byte[] moved = given().header("Authorization", auth)
        .get(bucket + "/objects/target/file.txt").then().statusCode(200).extract().asByteArray();
      assertArrayEquals(content, moved);

      given().header("Authorization", auth).contentType("application/json")
        .body(Map.of("source", "target/file.txt", "target", "target/file.txt"))
        .post(bucket + "/move").then().statusCode(400);
      given().header("Authorization", auth).contentType("application/json")
        .body(Map.of("source", "target", "target", "target/nested"))
        .post(bucket + "/move").then().statusCode(400);
    } finally {
      given().header("Authorization", auth).queryParam("force", true).delete(bucket).then().statusCode(204);
    }
  }

  @Test
  void uploadedUrlRoundTripsEncodedNamesAndRequiresAuthentication() {
    String name = "attachment-test-" + UUID.randomUUID();
    String bucket = "/projects/dev_test/buckets/" + name;
    String auth = tokens.getAuthorizationHeader();
    given().header("Authorization", auth).contentType("application/json")
      .body(Map.of("name", name, "visibility", "PRIVATE"))
      .post("/projects/dev_test/buckets").then().statusCode(200);
    try {
      String path = "app-123/form-456/" + UUID.randomUUID() + "_图,片.png";
      byte[] content = "attachment bytes".getBytes(StandardCharsets.UTF_8);
      var uploaded = given().header("Authorization", auth).contentType("application/octet-stream")
        .body(content).put(bucket + "/objects/" + path).then().statusCode(200)
        .body("path", equalTo(path)).extract().jsonPath();
      String url = uploaded.getString("url");
      assertTrue(url.startsWith("/api" + bucket + "/objects/app-123/form-456/"));
      assertTrue(url.contains("%2C"));
      assertFalse(url.contains(","));
      assertFalse(url.contains("http:"));
      byte[] downloaded = given().basePath("").urlEncodingEnabled(false)
        .header("Authorization", auth).get(url).then().statusCode(200).extract().asByteArray();
      assertArrayEquals(content, downloaded);
      given().basePath("").urlEncodingEnabled(false).get(url).then().statusCode(401);

      String emptyUrl = given().header("Authorization", auth).contentType("application/octet-stream")
        .body(new byte[0]).put(bucket + "/objects/app-123/form-456/empty.txt")
        .then().statusCode(200).extract().path("url");
      assertEquals(0, given().basePath("").header("Authorization", auth).get(emptyUrl)
        .then().statusCode(200).extract().asByteArray().length);
    } finally {
      given().header("Authorization", auth).queryParam("force", true).delete(bucket).then().statusCode(204);
    }
  }
}
