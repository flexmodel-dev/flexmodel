package dev.flexmodel.mcp;

import dev.flexmodel.SQLiteTestResource;
import dev.flexmodel.auth.dto.ApiKeyResponse;
import dev.flexmodel.auth.dto.CreateApiKeyRequest;
import dev.flexmodel.auth.service.ApiKeyService;
import dev.flexmodel.rest.TestTokenHelper;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MCP 注册回归：AppTools/DirectoryTools 改用 langchain4j 注解后，
 * 仍须经 quarkus.mcp.server.support-langchain4j-annotations 自动注册为 MCP 工具，
 * 且 API Key 服务身份链路（McpAuthHandler → McpToolAccess）保持可用。
 */
@QuarkusTest
@QuarkusTestResource(SQLiteTestResource.class)
class McpToolsRegistrationTest {
  @Inject
  ApiKeyService apiKeys;
  @Inject
  TestTokenHelper tokens;

  @Test
  void langchainToolsAreExposedOverMcp() {
    ApiKeyResponse key = apiKeys.create(new CreateApiKeyRequest("mcp-regression", null, false));
    String mcp = "/mcp?api_key=" + key.getKey();

    String sessionId = given()
      .contentType(ContentType.JSON)
      .accept("application/json, text/event-stream")
      .body(Map.of("jsonrpc", "2.0", "id", 1, "method", "initialize",
        "params", Map.of("protocolVersion", "2025-06-18", "capabilities", Map.of(),
          "clientInfo", Map.of("name", "test", "version", "1.0"))))
      .when().post(mcp)
      .then().statusCode(200)
      .extract().header("Mcp-Session-Id");

    var request = given().contentType(ContentType.JSON)
      .accept("application/json, text/event-stream")
      .header("Mcp-Session-Id", sessionId == null ? "" : sessionId);

    if (sessionId != null && !sessionId.isBlank()) {
      request.body(Map.of("jsonrpc", "2.0", "method", "notifications/initialized"))
        .when().post(mcp).then().statusCode(org.hamcrest.Matchers.anyOf(
          org.hamcrest.Matchers.is(200), org.hamcrest.Matchers.is(202)));
    }

    String tools = request
      .body(Map.of("jsonrpc", "2.0", "id", 2, "method", "tools/list"))
      .when().post(mcp)
      .then().statusCode(200)
      .extract().asString();
    assertTrue(tools.contains("list_apps"), tools);
    assertTrue(tools.contains("create_app_form"), tools);
    assertTrue(tools.contains("query_app_records"), tools);
    assertTrue(tools.contains("get_directory"), tools);
    assertTrue(tools.contains("resolve_members"), tools);

    String callResult = request
      .body(Map.of("jsonrpc", "2.0", "id", 3, "method", "tools/call",
        "params", Map.of("name", "list_apps", "arguments", Map.of())))
      .when().post(mcp)
      .then().statusCode(200)
      .extract().asString();
    assertTrue(callResult.contains("result"), callResult);
    assertTrue(!callResult.contains("请先登录"), callResult);

    apiKeys.delete(key.getId());
  }

  @Test
  void mcpEndpointRejectsMissingApiKey() {
    given().contentType(ContentType.JSON)
      .accept("application/json, text/event-stream")
      .body(Map.of("jsonrpc", "2.0", "id", 1, "method", "initialize",
        "params", Map.of("protocolVersion", "2025-06-18", "capabilities", Map.of(),
          "clientInfo", Map.of("name", "test", "version", "1.0"))))
      .when().post("/mcp")
      .then().statusCode(401);
  }
}
