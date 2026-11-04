package dev.flexmodel.apps.ai;

import dev.flexmodel.SQLiteTestResource;
import dev.flexmodel.rest.TestTokenHelper;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.Method;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 模型链路端到端测试（SSE 流式，统一会话 API）：
 * REST 会话（/ai/conversations）→ 声明式 AI Service → OpenAI 桩
 * （流式工具调用 + 流式回答）→ 共享工具查询真实应用数据 → token/tool/done 事件实时下发、完成后落库。
 * 覆盖 @应用名 提及解析、隐藏 appId 上下文、ChatMemory 历史装载与流式工具循环。
 */
@QuarkusTest
@QuarkusTestResource(SQLiteTestResource.class)
@QuarkusTestResource(OpenAiStubTestResource.class)
class AppAiModelFlowTest {
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
  void assistantResolvesMentionedAppAndStreamsToolAnswer() {
    String appId = "aimodel_" + Long.toHexString(System.nanoTime());
    call(Method.POST, "/apps", Map.of("projectId", appId, "name", "AI 模型链路测试"), 201);
    try {
      call(Method.POST, "/apps/" + appId + "/forms",
        Map.of("name", "orders", "label", "订单", "createDisplayField", true), 201);
      call(Method.POST, "/apps/" + appId + "/forms/orders/records",
        Map.of("name", "订单A"), 201);

      // 统一助手会话：robotId 省略使用内置 assistant。
      String conversationId = call(Method.POST, "/ai/conversations", Map.of(), 200)
        .then().body("robotId", containsString("assistant")).extract().path("id");
      // @应用名 提及解析为应用上下文（无需隐藏 appId），模型发起工具调用并流式返回。
      String sse = call(Method.POST, "/ai/conversations/" + conversationId + "/messages",
        Map.of("content", "@AI 模型链路测试 请查询订单表里的记录，并告诉我记录名称。"), 200)
        .then().contentType(containsString("text/event-stream")).extract().asString();
      assertTrue(sse.contains("\"type\":\"tool\"") && sse.contains("query_app_records"), sse);
      assertTrue(sse.contains("\"type\":\"token\"") && sse.contains("订单A"), sse);
      assertTrue(sse.contains("\"type\":\"done\""), sse);
      assertTrue(!sse.contains("\"type\":\"change\""), "只读工具不应发送变更：" + sse);

      // 桩服务收到两轮请求：第一轮按提及应用的系统提示发起工具调用，第二轮回传工具结果。
      List<String> bodies = OpenAiStubTestResource.requestBodies();
      assertTrue(bodies.size() >= 2, () -> "expected 2 model calls, got " + bodies.size());
      assertTrue(bodies.stream().anyMatch(body -> body.contains("query_app_records")
        && body.contains(appId) && body.contains("请查询订单表里的记录")));
      assertTrue(bodies.stream().anyMatch(body -> body.contains("订单A")));

      // done 事件后回复已落库：会话历史含 user + assistant 两条消息。
      List<Map<String, Object>> persisted = messages(conversationId);
      assertEquals(2, persisted.size());
      assertEquals("assistant", persisted.get(1).get("role"));
      assertTrue(((String) persisted.get(1).get("content")).contains("订单A"));

      // 再次发送时携带隐藏 appId（进入应用后前端自动传递），验证历史持久化与隐藏上下文。
      String secondSse = call(Method.POST, "/ai/conversations/" + conversationId + "/messages",
        Map.of("content", "谢谢，还有别的吗？", "appId", appId), 200)
        .then().contentType(containsString("text/event-stream")).extract().asString();
      assertTrue(secondSse.contains("\"type\":\"done\""), secondSse);
      assertEquals(4, messages(conversationId).size());
      // 隐藏 appId 已注入系统提示（进入应用后前端自动传递当前应用）。
      assertTrue(OpenAiStubTestResource.requestBodies().stream()
        .anyMatch(body -> body.contains("谢谢，还有别的吗") && body.contains(appId)));
    } finally {
      call(Method.DELETE, "/apps/" + appId, null, 204);
    }
  }

  @Test
  void successfulWriteStreamsActualChangeBeforeCompletion() {
    String appId = "airefresh_" + Long.toHexString(System.nanoTime());
    String conversationId = call(Method.POST, "/ai/conversations", Map.of(), 200).path("id");
    try {
      String sse = call(Method.POST, "/ai/conversations/" + conversationId + "/messages",
        Map.of("content", "自动刷新测试应用：" + appId), 200).asString();
      assertTrue(sse.contains("\"type\":\"change\"") && sse.contains("\"appId\":\"" + appId + "\""), sse);
      assertTrue(sse.contains("\"resource\":\"app\""), sse);
      assertTrue(sse.indexOf("\"type\":\"change\"") < sse.indexOf("\"type\":\"done\""), sse);
      call(Method.GET, "/apps/" + appId, null, 200);
    } finally {
      call(Method.DELETE, "/apps/" + appId, null, 204);
      call(Method.DELETE, "/ai/conversations/" + conversationId, null, 204);
    }
  }

  @Test
  void failedWriteDoesNotStreamChange() {
    String appId = "airefresh_" + Long.toHexString(System.nanoTime());
    call(Method.POST, "/apps", Map.of("projectId", appId, "name", "已有应用"), 201);
    String conversationId = call(Method.POST, "/ai/conversations", Map.of(), 200).path("id");
    try {
      String sse = call(Method.POST, "/ai/conversations/" + conversationId + "/messages",
        Map.of("content", "自动刷新测试应用：" + appId), 200).asString();
      assertTrue(sse.contains("\"type\":\"tool\""), sse);
      assertTrue(!sse.contains("\"type\":\"change\""), sse);
      assertTrue(sse.contains("\"type\":\"done\""), sse);
    } finally {
      call(Method.DELETE, "/apps/" + appId, null, 204);
      call(Method.DELETE, "/ai/conversations/" + conversationId, null, 204);
    }
  }

  @Test
  void committedChangeSurvivesLaterModelFailure() {
    String appId = "airefresh_" + Long.toHexString(System.nanoTime());
    String conversationId = call(Method.POST, "/ai/conversations", Map.of(), 200).path("id");
    try {
      String sse = call(Method.POST, "/ai/conversations/" + conversationId + "/messages",
        Map.of("content", "自动刷新测试应用：" + appId + " 创建后模拟失败"), 200).asString();
      assertTrue(sse.contains("\"type\":\"change\""), sse);
      assertTrue(sse.contains("\"type\":\"error\""), sse);
      assertTrue(!sse.contains("\"type\":\"done\""), sse);
      call(Method.GET, "/apps/" + appId, null, 200);
    } finally {
      call(Method.DELETE, "/apps/" + appId, null, 204);
      call(Method.DELETE, "/ai/conversations/" + conversationId, null, 204);
    }
  }

  @Test
  void assistantWithoutContextStreamsAnswerThroughModel() {
    String conversationId = call(Method.POST, "/ai/conversations", Map.of(), 200)
      .then().extract().path("id");
    try {
      // 无 @提及、无隐藏 appId：跨应用模式（系统提示引导 list_apps/create_app）。
      String sse = call(Method.POST, "/ai/conversations/" + conversationId + "/messages",
        Map.of("content", "帮我创建一个应用"), 200)
        .then().contentType(containsString("text/event-stream")).extract().asString();
      assertTrue(sse.contains("\"type\":\"token\""), sse);
      assertTrue(sse.contains("\"type\":\"done\""), sse);
      List<Map<String, Object>> persisted = messages(conversationId);
      assertEquals(2, persisted.size());
      assertEquals("assistant", persisted.get(1).get("role"));
      // 请求体携带全量工具定义（含跨应用模式可用的 list_apps）。
      assertTrue(OpenAiStubTestResource.requestBodies().stream()
        .anyMatch(body -> body.contains("帮我创建一个应用") && body.contains("list_apps")));
    } finally {
      call(Method.DELETE, "/ai/conversations/" + conversationId, null, 204);
    }
  }
}
