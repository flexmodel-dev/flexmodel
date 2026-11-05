package dev.flexmodel.apps.ai;

import com.sun.net.httpserver.HttpServer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OpenAI 兼容桩服务：查询订单意图的首轮调用返回 query_app_records 工具调用，其余返回固定答案；
 * 同时把模型收到的请求体记录下来供测试断言。工具调用的 appId 从请求系统提示中提取。
 * 支持非流式（JSON）与流式（SSE chunk）两种响应，流式按 OpenAI 增量格式下发。
 * 响应分支由请求内容决定（是否已含工具结果、是否查询意图），与测试执行顺序无关。
 */
public class OpenAiStubTestResource implements QuarkusTestResourceLifecycleManager {
  private static final List<String> REQUEST_BODIES = new ArrayList<>();
  private static final Pattern APP_ID = Pattern.compile("当前应用ID：([A-Za-z0-9_]+)");
  /**
   * 请求体为 pretty JSON（"stream" : true），用正则容忍冒号两侧空白。
   */
  private static final Pattern STREAMING = Pattern.compile("\"stream\"\\s*:\\s*true");

  private HttpServer server;

  static List<String> requestBodies() {
    return REQUEST_BODIES;
  }

  @Override
  public Map<String, String> start() {
    try {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    } catch (IOException e) {
      throw new IllegalStateException("无法启动 OpenAI 桩服务", e);
    }
    server.createContext("/v1/chat/completions", exchange -> {
      String body;
      synchronized (REQUEST_BODIES) {
        body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        REQUEST_BODIES.add(body);
      }
      Matcher matcher = APP_ID.matcher(body);
      String appId = matcher.find() ? matcher.group(1) : "unknown";
      // 已包含工具结果的是第二轮；查询订单意图且尚未携带工具结果的首轮发起工具调用。
      boolean toolResultRound = body.contains("\"role\" : \"tool\"");
      boolean toolCall = !toolResultRound && body.contains("请查询订单表里的记录");
      if (STREAMING.matcher(body).find()) {
        respondSse(exchange, toolCall ? toolCallChunks(appId) : contentChunks());
      } else {
        respondJson(exchange, toolCall ? toolCallJson(appId) : contentJson());
      }
    });
    server.start();
    return Map.of(
      "quarkus.langchain4j.openai.base-url", "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
      "quarkus.langchain4j.openai.api-key", "test-key");
  }

  @Override
  public void stop() {
    if (server != null) server.stop(0);
  }

  private static void respondJson(com.sun.net.httpserver.HttpExchange exchange, String response) throws IOException {
    byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(200, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }

  private static void respondSse(com.sun.net.httpserver.HttpExchange exchange, String chunks) throws IOException {
    byte[] bytes = chunks.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
    exchange.sendResponseHeaders(200, 0);
    OutputStream out = exchange.getResponseBody();
    out.write(bytes);
    out.flush();
    exchange.close();
  }

  /**
   * 流式工具调用：先给 tool_calls 增量，再以 finish_reason=tool_calls 结束。
   */
  private static String toolCallChunks(String appId) {
    return """
      data: {"id":"chatcmpl-1","object":"chat.completion.chunk","created":1700000000,"model":"stub-model","choices":[{"index":0,"delta":{"role":"assistant","tool_calls":[{"index":0,"id":"call_1","type":"function","function":{"name":"query_app_records","arguments":""}}]},"finish_reason":null}]}

      data: {"id":"chatcmpl-1","object":"chat.completion.chunk","created":1700000000,"model":"stub-model","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{\\"appId\\":\\"%s\\",\\"form\\":\\"orders\\"}"}}]},"finish_reason":null}]}

      data: {"id":"chatcmpl-1","object":"chat.completion.chunk","created":1700000000,"model":"stub-model","choices":[{"index":0,"delta":{},"finish_reason":"tool_calls"}]}

      data: [DONE]

      """.formatted(appId);
  }

  /**
   * 流式最终回答：content 分两个增量下发，finish_reason=stop。
   */
  private static String contentChunks() {
    return """
      data: {"id":"chatcmpl-2","object":"chat.completion.chunk","created":1700000000,"model":"stub-model","choices":[{"index":0,"delta":{"role":"assistant","content":"共 1 条"},"finish_reason":null}]}

      data: {"id":"chatcmpl-2","object":"chat.completion.chunk","created":1700000000,"model":"stub-model","choices":[{"index":0,"delta":{"content":"订单记录：订单A。"},"finish_reason":null}]}

      data: {"id":"chatcmpl-2","object":"chat.completion.chunk","created":1700000000,"model":"stub-model","choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}

      data: [DONE]

      """;
  }

  private static String toolCallJson(String appId) {
    return """
      {"id":"chatcmpl-1","object":"chat.completion","created":1700000000,"model":"stub-model",
      "choices":[{"index":0,"message":{"role":"assistant","content":null,"tool_calls":[
      {"id":"call_1","type":"function","function":{"name":"query_app_records","arguments":"{\\"appId\\":\\"%s\\",\\"form\\":\\"orders\\"}"}}
      ]},"finish_reason":"tool_calls"}],
      "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
      """.formatted(appId);
  }

  private static String contentJson() {
    return """
      {"id":"chatcmpl-2","object":"chat.completion","created":1700000000,"model":"stub-model",
      "choices":[{"index":0,"message":{"role":"assistant","content":"共 1 条订单记录：订单A。"},
      "finish_reason":"stop"}],
      "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
      """;
  }
}
