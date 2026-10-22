package dev.flexmodel.notification;

import dev.flexmodel.SQLiteTestResource;
import dev.flexmodel.common.config.web.jwt.JwtService;
import dev.flexmodel.session.SessionFactory;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.*;
import jakarta.inject.Inject;
import org.junit.jupiter.api.*;

import java.time.*;
import java.util.*;
import java.net.*;
import java.net.http.*;
import java.io.*;
import java.util.concurrent.*;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestProfile(NotificationResourceTest.Profile.class)
@QuarkusTestResource(SQLiteTestResource.class)
class NotificationResourceTest {
  public static class Profile implements QuarkusTestProfile {
    public Map<String, String> getConfigOverrides() {
      return Map.of("quarkus.scheduler.enabled", "false", "quarkus.rabbitmq.devservices.enabled", "false");
    }
  }

  @Inject
  NotificationRepository repository;
  @Inject
  NotificationService service;
  @Inject
  dev.flexmodel.common.ProjectUnitOfWork transactions;
  @Inject
  dev.flexmodel.common.messaging.ReliableMessages messages;
  @Inject
  SessionFactory sessions;
  @Inject
  JwtService jwt;
  @Inject
  dev.flexmodel.auth.service.ApiKeyService apiKeys;

  String user() {
    String id = "notice_" + UUID.randomUUID().toString().replace("-", "");
    try (var session = sessions.createSession()) {
      session.data().insert("f_user", new HashMap<>(Map.of("id", id, "name", id, "password_hash", "unused", "status", "ACTIVE")));
    }
    return id;
  }

  io.restassured.specification.RequestSpecification api(String user) {
    return given().header("Authorization", "Bearer " + jwt.sign(user, Duration.ofMinutes(5))).contentType("application/json");
  }

  Map<String, Object> payload(String category) {
    return Map.of("category", category, "title", "审批提醒", "content", "纯文本 <b>内容</b>", "app_name", "测试应用", "instance_id", "", "task_id", "", "occurred_at", LocalDateTime.now().toString());
  }

  @Test
  void inboxIsolationPaginationAndIdempotentReads() {
    String user = user(), other = user();
    for (int i = 0; i < 32; i++) repository.deliver("dev_test", "event-" + i, user, payload("approval"));
    repository.deliver("other_app", "other", user, payload("workflow"));
    String id = api(user).get("/notifications?status=all&size=30&page=2").then().statusCode(200).body("list.size()", equalTo(3)).extract().path("list[0].id");
    api(other).get("/notifications/" + id).then().statusCode(404);
    api(other).put("/notifications/" + id + "/read").then().statusCode(404);
    api(user).get("/notifications/summary").then().statusCode(200).body("unread", equalTo(33));
    api(user).put("/notifications/" + id + "/read").then().statusCode(200).body("updated", equalTo(1));
    api(user).put("/notifications/" + id + "/read").then().statusCode(200).body("updated", equalTo(0));
    String received = repository.detail(user, id).get("receivedAt").toString();
    for (int i = 0; i < 32; i++) repository.deliver("dev_test", "event-" + i, user, payload("approval"));
    assertEquals(received, repository.detail(user, id).get("receivedAt").toString());
    assertNotNull(repository.detail(user, id).get("readAt"));
    api(user).body(Map.of("appId", "dev_test", "category", "approval")).post("/notifications/read-all").then().statusCode(200);
    api(user).get("/notifications/summary").then().statusCode(200).body("unread", equalTo(1));
    assertEquals(35, repository.events(user, 0, 100).size());
    given().get("/notifications/summary").then().statusCode(401);
  }

  @Test
  void sourceRollbackCannotDeliverAndRetryPreservesReadState() {
    String user = user(), key = UUID.randomUUID().toString();
    assertThrows(IllegalStateException.class, () -> transactions.execute("dev_test", () -> {
      service.enqueue("dev_test", key, "", "", "workflow", "标题", "正文", List.of(user));
      throw new IllegalStateException("rollback");
    }));
    messages.drain("dev_test", 100);
    assertEquals(0L, NotificationRepository.number(repository.state(user), "version"));
    transactions.execute("dev_test", () -> {
      service.enqueue("dev_test", key, "", "", "workflow", "标题", "正文", List.of(user));
      return null;
    });
    messages.drain("dev_test", 100);
    var list = repository.list(user, "all", null, null, 1, 30);
    @SuppressWarnings("unchecked") var row = ((List<Map<String, Object>>) list.get("list")).getFirst();
    repository.read(user, row.get("id").toString(), null, null);
    service.handle("dev_test", "notification:" + key, new HashMap<>(Map.of("recipients", List.of(user), "category", "workflow", "title", "重复标题", "content", "重复内容", "app_name", "测试应用", "occurred_at", LocalDateTime.now().toString())));
    assertEquals(0L, NotificationRepository.number(repository.state(user), "unread"));
  }

  record Frame(String type, Map<String, Object> data) {
  }

  Frame frame(BufferedReader reader, String wanted) throws Exception {
    return CompletableFuture.supplyAsync(() -> {
      try {
        String line, type = "";
        StringBuilder data = new StringBuilder();
        while ((line = reader.readLine()) != null) {
          if (line.startsWith("event:")) type = line.substring(6).trim();
          if (line.startsWith("data:")) data.append(line.substring(5).trim());
          if (line.isEmpty()) {
            if (type.equals(wanted) && !data.isEmpty())
              return new Frame(type, dev.flexmodel.JsonUtils.parseToMap(data.toString()));
            data.setLength(0);
            type = "";
          }
        }
        throw new IllegalStateException("SSE ended");
      } catch (IOException error) {
        throw new RuntimeException(error);
      }
    }).get(8, TimeUnit.SECONDS);
  }

  @Test
  void authenticatedStreamPushesImmediatelyAndReplaysWithoutAlerts() throws Exception {
    String user = user();
    repository.summary(user);
    String base = "http://localhost:" + io.restassured.RestAssured.port + "/api/notifications/stream";
    var client = HttpClient.newHttpClient();
    try {
      var request = HttpRequest.newBuilder(URI.create(base)).header("Authorization", "Bearer " + jwt.sign(user, Duration.ofMinutes(5))).build();
      var response = client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream()).get(8, TimeUnit.SECONDS);
      assertEquals(200, response.statusCode());
      assertEquals("no", response.headers().firstValue("X-Accel-Buffering").orElse(""));
      try (var body = response.body(); var reader = new BufferedReader(new InputStreamReader(body))) {
        frame(reader, "ready");
        long start = System.nanoTime();
        repository.deliver("dev_test", "live", user, payload("approval"));
        var event = frame(reader, "notification.created");
        assertEquals(false, event.data.get("replay"));
        assertEquals("1", event.data.get("version"));
        assertTrue(Duration.ofNanos(System.nanoTime() - start).toMillis() < 2000);
        repository.read(user, repository.events(user, 0, 1).getFirst().get("payload") instanceof Map<?, ?> p ? ((Map<?, ?>) p.get("notification")).get("id").toString() : "", null, null);
        frame(reader, "notification.read");
      }
      var reconnect = HttpRequest.newBuilder(URI.create(base)).header("Authorization", "Bearer " + jwt.sign(user, Duration.ofMinutes(5))).header("Last-Event-ID", "0").build();
      var replay = client.sendAsync(reconnect, HttpResponse.BodyHandlers.ofInputStream()).get(8, TimeUnit.SECONDS);
      try (var reader = new BufferedReader(new InputStreamReader(replay.body()))) {
        assertEquals(true, frame(reader, "notification.created").data.get("replay"));
        frame(reader, "notification.read");
        frame(reader, "ready");
      }
    } finally {
      client.shutdownNow();
    }
  }

  @Test
  void templatesArePlainTextAndRejectExpressions() {
    assertEquals("费用 12 / ", NotificationTemplates.render("费用 {{record.amount}} / {{missing}}", Map.of("record", Map.of("amount", 12)), 200));
    assertThrows(RuntimeException.class, () -> NotificationTemplates.paths("{{record.amount + 1}}"));
    assertThrows(RuntimeException.class, () -> NotificationTemplates.render("{{missing}}", Map.of(), 200));
  }

  @Test
  void oversizedReplayUsesSnapshotAndTokenExpiryClosesStream() throws Exception {
    String user = user();
    try (var session = sessions.createSession()) {
      session.startTransaction();
      session.data().insert("f_notification_stream", new HashMap<>(Map.of("id", user, "version", 1001L, "unread", 0L)));
      for (long i = 1; i <= 1001; i++)
        session.data().insert("f_notification_event", new HashMap<>(Map.of("id", user + ":" + i, "recipient_id", user, "version", i, "type", "notification.read", "payload", Map.of("version", Long.toString(i), "unread", 0), "published", true, "created_at", LocalDateTime.now())));
      session.commit();
    }
    var client = HttpClient.newHttpClient();
    try {
      var request = HttpRequest.newBuilder(URI.create("http://localhost:" + io.restassured.RestAssured.port + "/api/notifications/stream"))
        .header("Authorization", "Bearer " + jwt.sign(user, Duration.ofSeconds(4))).header("Last-Event-ID", "0").build();
      var response = client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream()).get(4, TimeUnit.SECONDS);
      try (var reader = new BufferedReader(new InputStreamReader(response.body()))) {
        assertEquals("1001", frame(reader, "snapshot").data.get("version"));
        frame(reader, "ready");
        assertNull(CompletableFuture.supplyAsync(() -> {
          try {
            return reader.readLine();
          } catch (IOException error) {
            throw new RuntimeException(error);
          }
        }).get(6, TimeUnit.SECONDS));
      }
    } finally {
      client.shutdownNow();
    }
  }

  @Test
  void serviceKeysCannotReadPersonalInboxAndConcurrentRetriesStayUnique() throws Exception {
    var key = apiKeys.create(new dev.flexmodel.auth.dto.CreateApiKeyRequest("notification-service", null, false));
    given().header("Authorization", "Bearer " + key.getKey()).get("/notifications/summary").then().statusCode(403);
    given().header("Authorization", "Bearer " + key.getKey()).get("/notifications/stream").then().statusCode(403);
    String user = user();
    repository.summary(user);
    try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
      var a = pool.submit(() -> repository.deliver("dev_test", "same", user, payload("approval")));
      var b = pool.submit(() -> repository.deliver("dev_test", "same", user, payload("approval")));
      a.get(5, TimeUnit.SECONDS);
      b.get(5, TimeUnit.SECONDS);
    }
    assertEquals(1L, NotificationRepository.number(repository.state(user), "unread"));
    assertEquals(1, repository.events(user, 0, 100).size());
  }

  @Test
  void browserRoundTripAgainstRealBackend() throws Exception {
    var script = java.nio.file.Path.of("../flexmodel-apps-ui/e2e/notifications-backend.mjs").toAbsolutePath().normalize();
    if (!java.nio.file.Files.exists(script))
      script = java.nio.file.Path.of("flexmodel-apps-ui/e2e/notifications-backend.mjs").toAbsolutePath().normalize();
    var output = java.nio.file.Files.createTempFile("notification-browser-", ".log");
    var builder = new ProcessBuilder("node", script.toString()).redirectErrorStream(true).redirectOutput(output.toFile());
    builder.environment().put("NOTIFICATION_TEST_API", "http://127.0.0.1:" + io.restassured.RestAssured.port);
    builder.environment().put("NOTIFICATION_TEST_TOKEN", jwt.sign("admin", Duration.ofMinutes(10)));
    var process = builder.start();
    boolean finished = false;
    long deadline = System.nanoTime() + Duration.ofSeconds(150).toNanos();
    while (System.nanoTime() < deadline && !(finished = process.waitFor(100, TimeUnit.MILLISECONDS))) {
      try {
        messages.drain("dev_test", 100);
      } catch (jakarta.ws.rs.ServiceUnavailableException busy) {
      }
    }
    if (!finished) {
      process.descendants().forEach(ProcessHandle::destroyForcibly);
      process.destroyForcibly();
    }
    String log = java.nio.file.Files.readString(output);
    java.nio.file.Files.deleteIfExists(output);
    assertTrue(finished, "浏览器通知验证超时");
    assertEquals(0, process.exitValue(), log);
  }
}
