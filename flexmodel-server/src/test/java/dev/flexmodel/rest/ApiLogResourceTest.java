package dev.flexmodel.rest;

import dev.flexmodel.SQLiteTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

/**
 * @author cjbi
 */
@QuarkusTest
@QuarkusTestResource(SQLiteTestResource.class)
public class ApiLogResourceTest {

  @Inject
  TestTokenHelper testTokenHelper;

  @Test
  void testFindApiLogs() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .get(Resources.ROOT_PATH + "/projects/dev_test/logs")
      .then()
      .statusCode(200);
  }

  @Test
  void testStat() {
    given()
      .header("Authorization", testTokenHelper.getAuthorizationHeader())
      .when()
      .get(Resources.ROOT_PATH + "/projects/dev_test/logs/stat")
      .then()
      .statusCode(200);
  }
}
