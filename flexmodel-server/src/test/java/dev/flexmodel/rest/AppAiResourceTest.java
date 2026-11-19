package dev.flexmodel.rest;

import dev.flexmodel.SQLiteTestResource;
import dev.flexmodel.apps.ai.AppAiRepository;
import dev.flexmodel.session.SessionFactory;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.Method;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(SQLiteTestResource.class)
@TestProfile(AppAiResourceTest.NoModelProfile.class)
class AppAiResourceTest {
  /**
   * 固定关闭真实模型：未配置模型时发送消息返回 500，其余会话生命周期不受影响。
   */
  public static class NoModelProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      // api-key 不能覆盖为空字符串（扩展侧校验拒绝），用哨兵值 "dummy" 表示未配置。
      return Map.of("quarkus.langchain4j.openai.api-key", "dummy");
    }
  }

  @Inject
  TestTokenHelper tokens;
  @Inject
  AppAiRepository aiRepository;
  @Inject
  SessionFactory sessions;

  private Map<String, Object> robotInput(String name) {
    return new LinkedHashMap<>(Map.of(
      "name", name,
      "description", "查询与分析当前应用中的业务信息",
      "greeting", "你好，我可以帮你查询记录。",
      "suggestions", List.of("最近有哪些新增记录？", "帮我汇总当前业务情况"),
      "prompt", "围绕当前应用回答业务问题。",
      "enabled", true));
  }

  private String createApp() {
    String suffix = Long.toHexString(System.nanoTime());
    String appId = "ai_" + suffix;
    call("admin", Method.POST, "/apps", Map.of("projectId", appId, "name", "AI 测试应用"), 201);
    return appId;
  }

  private String createRobot(String appId) {
    return call("admin", Method.POST, "/apps/" + appId + "/ai/robots", robotInput("数据查询助手"), 200)
      .then().body("name", equalTo("数据查询助手")).extract().path("id");
  }

  private String createGroup(String appId) {
    return call("admin", Method.POST, "/apps/" + appId + "/navigation",
      Map.of("type", "GROUP", "name", "应用"), 201).then().extract().path("id");
  }

  private Response call(String user, Method method, String path, Object body, int status) {
    var request = given().header("Authorization", tokens.getAuthorizationHeader(user));
    if (body != null) request.contentType("application/json").body(body);
    Response response = request.request(method, path);
    response.then().statusCode(status);
    return response;
  }

  @Test
  void robotLifecycleAndPersonalConversations() {
    String appId = createApp();
    try {
      String robotId = createRobot(appId);
      call("admin", Method.GET, "/apps/" + appId + "/ai/robots", null, 200)
        .then().body("size()", equalTo(1)).body("[0].id", equalTo(robotId));

      call("admin", Method.PUT, "/apps/" + appId + "/ai/robots/" + robotId,
        Map.of("name", "业务助手", "description", "", "greeting", "你好", "suggestions", List.of(),
          "prompt", "", "enabled", true), 200)
        .then().body("name", equalTo("业务助手"));
      call("admin", Method.PUT, "/apps/" + appId + "/ai/robots/" + robotId,
        Map.of("name", " ", "enabled", true), 400);
      call("admin", Method.PUT, "/apps/" + appId + "/ai/robots/" + robotId,
        Map.of("name", "业务助手", "suggestions", List.of("1", "2", "3", "4", "5", "6", "7"), "enabled", true), 400);

      String conversationId = call("admin", Method.POST, "/ai/conversations",
        Map.of("robotId", robotId), 200)
        .then().body("robotId", equalTo(robotId))
        .body("title", equalTo("新对话")).extract().path("id");
      // 模型未配置时发送返回 500，消息不落库。
      call("admin", Method.POST, "/ai/conversations/" + conversationId + "/messages",
        Map.of("content", "最近有哪些新增记录？"), 500);
      call("admin", Method.POST, "/ai/conversations/" + conversationId + "/messages",
        Map.of("content", " "), 400);
      call("admin", Method.GET, "/ai/conversations?robotId=" + robotId, null, 200)
        .then().body("size()", equalTo(1));

      call("admin", Method.DELETE, "/ai/conversations/" + conversationId, null, 204);
      call("admin", Method.GET, "/ai/conversations?robotId=" + robotId, null, 200)
        .then().body("size()", equalTo(0));

      call("admin", Method.DELETE, "/apps/" + appId + "/ai/robots/" + robotId, null, 204);
      call("admin", Method.GET, "/apps/" + appId + "/ai/robots", null, 200)
        .then().body("size()", equalTo(0));
    } finally {
      call("admin", Method.DELETE, "/apps/" + appId, null, 204);
    }
  }

  @Test
  void navigationAndWorkbenchMustReferenceExistingRobot() {
    String appId = createApp();
    try {
      String topId = createGroup(appId);
      String robotId = createRobot(appId);
      var chatbotNav = new LinkedHashMap<String, Object>(Map.of("type", "ENTRY", "name", "AI 对话",
        "iconName", "Bot", "parentId", topId,
        "destination", Map.of("type", "BUILTIN", "id", "chatbot", "robotId", robotId)));
      String navId = call("admin", Method.POST, "/apps/" + appId + "/navigation", chatbotNav, 201)
        .then().body("destination.id", equalTo("chatbot"))
        .body("destination.robotId", equalTo(robotId)).extract().path("id");
      call("admin", Method.GET, "/apps/" + appId + "/navigation", null, 200)
        .then().body("[0].children.find { it.id == '" + navId + "' }.destination.robotId", equalTo(robotId));

      // 机器人可选：未配置时页面默认第一个启用的机器人（与工作台绑定一致）。
      call("admin", Method.POST, "/apps/" + appId + "/navigation",
        Map.of("type", "ENTRY", "name", "默认机器人对话", "parentId", topId,
          "destination", Map.of("type", "BUILTIN", "id", "chatbot")), 201)
        .then().body("destination.id", equalTo("chatbot"))
        .body("destination.robotId", nullValue());
      call("admin", Method.POST, "/apps/" + appId + "/navigation",
        Map.of("type", "ENTRY", "name", "不存在的机器人", "parentId", topId,
          "destination", Map.of("type", "BUILTIN", "id", "chatbot", "robotId", "missing")), 400);
      call("admin", Method.POST, "/apps/" + appId + "/navigation",
        Map.of("type", "ENTRY", "name", "工作台", "parentId", topId,
          "destination", Map.of("type", "BUILTIN", "id", "workbench", "robotId", robotId)), 400);

      Map<String, Object> aiComponent = Map.of("id", "ai_card", "title", "问问 AI 助手",
        "placement", Map.of("x", 0, "y", 0, "w", 6, "h", 3),
        "config", Map.of("type", "ai", "robotId", robotId, "displayMode", "shortcut", "showSuggestions", true));
      String workbenchId = call("admin", Method.POST, "/apps/" + appId + "/workbenches",
        Map.of("name", "业务工作台", "layout", Map.of("schemaVersion", 1, "components", List.of(aiComponent))), 201)
        .then().body("layout.components[0].config.type", equalTo("ai"))
        .body("layout.components[0].config.robotId", equalTo(robotId)).extract().path("id");

      call("admin", Method.POST, "/apps/" + appId + "/workbenches",
        Map.of("name", "非法组件", "layout", Map.of("schemaVersion", 1, "components", List.of(
          Map.of("id", "ai_bad", "title", "AI", "placement", Map.of("x", 0, "y", 0, "w", 6, "h", 3),
            "config", Map.of("type", "ai", "robotId", "missing", "displayMode", "shortcut"))))), 400);
      call("admin", Method.POST, "/apps/" + appId + "/workbenches",
        Map.of("name", "非法展示方式", "layout", Map.of("schemaVersion", 1, "components", List.of(
          Map.of("id", "ai_mode", "title", "AI", "placement", Map.of("x", 0, "y", 0, "w", 6, "h", 3),
            "config", Map.of("type", "ai", "robotId", robotId, "displayMode", "popup"))))), 400);

      // 机器人被导航与工作台引用时不允许删除，解除后可以删除。
      call("admin", Method.DELETE, "/apps/" + appId + "/ai/robots/" + robotId, null, 400);
      // 应用至少保留一个工作台，先补建一个空工作台再删除引用机器人的那个。
      String fallbackWorkbenchId = call("admin", Method.POST, "/apps/" + appId + "/workbenches",
        Map.of("name", "备用工作台", "layout", Map.of("schemaVersion", 1, "components", List.of())), 201)
        .then().extract().path("id");
      call("admin", Method.DELETE, "/apps/" + appId + "/workbenches/" + workbenchId, null, 204);
      call("admin", Method.DELETE, "/apps/" + appId + "/navigation/" + navId, null, 204);
      call("admin", Method.DELETE, "/apps/" + appId + "/ai/robots/" + robotId, null, 204);
    } finally {
      call("admin", Method.DELETE, "/apps/" + appId, null, 204);
    }
  }

  @Test
  void assistantConversationWorksForAnyUserWithoutRobotConfig() {
    String appId = createApp();
    try {
      call("admin", Method.GET, "/apps/" + appId + "/ai/robots", null, 200)
        .then().body("size()", equalTo(0));
      // 统一助手会话：robotId 省略使用保留 ID assistant，不写机器人表。
      String conversationId = call("admin", Method.POST, "/ai/conversations", Map.of(), 200)
        .then().body("robotId", equalTo("assistant"))
        .body("title", equalTo("新对话")).extract().path("id");
      call("admin", Method.POST, "/ai/conversations/" + conversationId + "/messages",
        Map.of("content", "帮我汇总当前业务情况"), 500);
      call("admin", Method.GET, "/ai/conversations", null, 200)
        .then().body("size()", equalTo(1));
      call("admin", Method.DELETE, "/ai/conversations/" + conversationId, null, 204);
      // 内置助手不写机器人表；助手会话不归属应用。
      call("admin", Method.GET, "/apps/" + appId + "/ai/robots", null, 200)
        .then().body("size()", equalTo(0));
      call("admin", Method.GET, "/ai/conversations", null, 200)
        .then().body("size()", equalTo(0));
    } finally {
      call("admin", Method.DELETE, "/apps/" + appId, null, 204);
    }
  }

  @Test
  void nonManagerCannotConfigureRobots() {
    String appId = createApp();
    try {
      String robotId = createRobot(appId);
      String outsider = "u_" + Long.toHexString(System.nanoTime());
      try (var session = sessions.createSession()) {
        session.dsl().insertInto("f_user").values(new LinkedHashMap<>(Map.of(
          "id", outsider, "name", outsider, "password_hash", "unused", "status", "ACTIVE"))).execute();
      }
      call(outsider, Method.POST, "/apps/" + appId + "/ai/robots", robotInput("无权限"), 403);
      call(outsider, Method.PUT, "/apps/" + appId + "/ai/robots/" + robotId, robotInput("无权限"), 403);
      call(outsider, Method.DELETE, "/apps/" + appId + "/ai/robots/" + robotId, null, 403);
    } finally {
      call("admin", Method.DELETE, "/apps/" + appId, null, 204);
    }
  }

  @Test
  void deletingAppRemovesRobotsAndConversations() {
    String appId = createApp();
    String robotId = createRobot(appId);
    String conversationId = call("admin", Method.POST, "/ai/conversations",
      Map.of("robotId", robotId), 200).then().extract().path("id");
    assertEquals(1, aiRepository.robots(appId).size());
    assertNotNull(aiRepository.conversation(robotId, "admin", conversationId));

    call("admin", Method.DELETE, "/apps/" + appId, null, 204);

    assertTrue(aiRepository.robots(appId).isEmpty());
    assertNull(aiRepository.conversation(robotId, "admin", conversationId));
  }
}
