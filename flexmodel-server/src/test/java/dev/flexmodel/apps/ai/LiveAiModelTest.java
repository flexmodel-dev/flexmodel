package dev.flexmodel.apps.ai;

import dev.flexmodel.SQLiteTestResource;
import dev.flexmodel.rest.TestTokenHelper;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.Method;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实模型联调测试（SSE 流式，统一会话 API）：仅在提供 FLEXMODEL_AI_API_KEY
 * （以及 base-url/model）时运行，普通 CI 不依赖外网。用于验证配置映射、
 * 隐藏 appId 应用上下文、流式工具调用与真实记录查询链路。
 */
@QuarkusTest
@QuarkusTestResource(SQLiteTestResource.class)
@EnabledIfEnvironmentVariable(named = "FLEXMODEL_AI_API_KEY", matches = ".+")
class LiveAiModelTest {
  @Inject
  TestTokenHelper tokens;

  private Response call(Method method, String path, Object body, int status) {
    var request = given().header("Authorization", tokens.getAuthorizationHeader());
    if (body != null) request.contentType("application/json").body(body);
    Response response = request.request(method, path);
    response.then().statusCode(status);
    return response;
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> messages(String conversationId) {
    List<Map<String, Object>> conversations = call(Method.GET, "/ai/conversations", null, 200)
      .then().extract().jsonPath().getList(".");
    return conversations.stream()
      .filter(conversation -> conversationId.equals(conversation.get("id")))
      .findFirst().map(conversation -> (List<Map<String, Object>>) conversation.get("messages"))
      .orElseThrow();
  }

  @Test
  void assistantStreamsRealRecordsWithConfiguredModel() {
    String appId = "ailive_" + Long.toHexString(System.nanoTime());
    call(Method.POST, "/apps", Map.of("projectId", appId, "name", "AI 联调测试"), 201);
    try {
      call(Method.POST, "/apps/" + appId + "/forms",
        Map.of("name", "orders", "label", "订单", "createDisplayField", true), 201);
      call(Method.POST, "/apps/" + appId + "/forms/orders/records",
        Map.of("name", "订单A"), 201);

      String conversationId = call(Method.POST, "/ai/conversations", Map.of(), 200)
        .then().extract().path("id");
      // SSE 流式返回：token 增量实时下发，done 事件后回复落库；
      // appId 为隐藏的应用上下文（模拟进入应用后前端自动传递）。
      String sse = call(Method.POST, "/ai/conversations/" + conversationId + "/messages",
        Map.of("content", "请查询订单表里有哪条记录，并告诉我记录名称。", "appId", appId), 200)
        .then().contentType(containsString("text/event-stream")).extract().asString();
      assertTrue(sse.contains("\"type\":\"token\""), sse);
      assertTrue(sse.contains("\"type\":\"done\""), sse);

      List<Map<String, Object>> persisted = messages(conversationId);
      assertEquals(2, persisted.size());
      assertEquals("assistant", persisted.get(1).get("role"));
      assertTrue(((String) persisted.get(1).get("content")).contains("订单A"),
        (String) persisted.get(1).get("content"));
    } finally {
      call(Method.DELETE, "/apps/" + appId, null, 204);
    }
  }
}
