package dev.flexmodel.apps.flow;

import dev.flexmodel.SQLiteTestResource;
import dev.flexmodel.apps.AppService;
import dev.flexmodel.apps.dto.AppCreateRequest;
import dev.flexmodel.apps.form.AppFormService;
import dev.flexmodel.apps.form.dto.AppFormCreateRequest;
import dev.flexmodel.apps.record.AppRecordService;
import dev.flexmodel.common.*;
import dev.flexmodel.common.messaging.*;
import dev.flexmodel.project.ProjectService;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static dev.flexmodel.common.Values.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(FlowDatabaseResource.class)
@TestProfile(AppFlowIntegrationTest.Profile.class)
class AppFlowIntegrationTest {
  public static class Profile implements io.quarkus.test.junit.QuarkusTestProfile {
    public Map<String, String> getConfigOverrides() {
      return Map.of("quarkus.scheduler.enabled", "false");
    }
  }
  @Inject
  AppService apps;
  @Inject
  AppFormService forms;
  @Inject
  AppRecordService records;
  @Inject
  AppFlowService flows;
  @Inject
  AppFlowRuntime runtime;
  @Inject
  AppFlowBindings bindings;
  @Inject
  AppFlowState state;
  @Inject
  AppFlowInstanceRepository instances;
  @Inject
  ReliableMessages messages;
  @Inject
  ProjectUnitOfWork transactions;
  @Inject
  ProjectService projects;
  @Inject
  SessionContext current;
  @Inject
  AppApprovalService tasks;
  @Inject
  dev.flexmodel.common.config.web.jwt.JwtService jwt;
  @Inject
  dev.flexmodel.auth.service.ApiKeyService keys;
  String app, schema;

  @BeforeEach
  void setup() {
    current.setUserId("admin");
    current.setAuthenticatedUser(true);
    app = "wf" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    apps.createApp(new AppCreateRequest("工作流测试", "", app, "Workflow", "blue"));
    schema = projects.resolveDatabaseName(app);
    current.setProjectId(app);
    current.setProjectDatabaseName(schema);
    forms.createForm(app, new AppFormCreateRequest("订单", "orders", "", true));
  }

  Map<String, Object> node(String id, String kind, Map<String, Object> config) {
    var cfg = new LinkedHashMap<>(config);
    cfg.put("kind", kind);
    return new LinkedHashMap<>(Map.of("id", id, "type", kind, "data", Map.of("title", id, "config", cfg)));
  }

  Map<String, Object> draft(Map<String, Object> trigger, List<Map<String, Object>> tasks) {
    var nodes = new ArrayList<Map<String, Object>>();
    nodes.add(node("start", "start", Map.of("trigger", trigger)));
    nodes.addAll(tasks);
    nodes.add(node("end", "end", Map.of()));
    return new LinkedHashMap<>(Map.of("name", "工作流", "description", "", "definition", Map.of("nodes", nodes)));
  }

  Map<String, Object> publish(Map<String, Object> body) {
    var flow = flows.save(app, null, body);
    assertTrue(flows.validate(app, flow).isEmpty(), () -> flows.validate(app, flow).toString());
    return flows.publish(app, text(flow, "id"));
  }

  void drain() {
    for (int i = 0; i < 20; i++) if (messages.drain(app, 100) == 0) break;
  }

  String record(String name) {
    return text(records.createRecord(app, "orders", Map.of("name", name), null, null, null), "id");
  }

  @Test
  void manualPublicationIsIndependentFromDraftAndRequestIsIdempotent() {
    String record = record("订单A");
    var flow = publish(draft(Map.of("type", "MANUAL", "formName", "orders", "lockRecord", false), List.of()));
    String id = text(flow, "id"), deployment = bindings.find(app, id).getDeploymentId();
    var edited = new LinkedHashMap<>(flow);
    edited.put("name", "尚未发布的修改");
    flows.save(app, id, edited);
    assertEquals(deployment, bindings.find(app, id).getDeploymentId());
    var run = runtime.manual(app, id, record, "key", Map.of("remark", "a"));
    assertEquals(text(run, "id"), text(runtime.manual(app, id, record, "key", Map.of("remark", "a")), "id"));
    assertThrows(jakarta.ws.rs.ClientErrorException.class, () -> runtime.manual(app, id, record, "key", Map.of("remark", "b")));
    records.updateRecord(app, "orders", record, Map.of("name", "允许编辑"), null, null, null);
    drain();
    assertEquals("SUCCEEDED", text(state.run(app, text(run, "id")), "status"));
    assertEquals("订单A", object(state.variables(app, run).get("record")).get("name"));
  }

  @Test
  void scheduledFlowStartsWithoutRecordAndQueryOutputsFeedTheNextNode() {
    record("订单A");
    var query = node("query", "query", Map.of("form", Map.of("kind", "app", "name", "orders"), "queryMode", "many", "conditions", List.of(), "output", "ordersResult"));
    var flow = publish(draft(Map.of("type", "SCHEDULE", "cron", "0 0 9 * * ?", "timezone", "Asia/Shanghai"), List.of(query)));
    var binding = bindings.find(app, text(flow, "id"));
    var run = runtime.automatic(app, binding, "tick-1", Map.of("type", "SCHEDULE", "scheduledAt", "2026-11-08T01:00:00Z"), Map.of());
    assertTrue(runtime.automatic(app, binding, "tick-2", Map.of("type", "SCHEDULE"), Map.of()).isEmpty());
    drain();
    var completed = state.run(app, text(run, "id"));
    assertEquals("SUCCEEDED", text(completed, "status"));
    var vars = state.variables(app, completed);
    assertFalse(vars.containsKey("record"));
    assertTrue(object(vars.get("input")).isEmpty());
    assertEquals(1, ((List<?>) object(vars.get("nodes")).get("query")).size());
    assertTrue(bindings.logs(app, text(flow, "id")).stream().anyMatch(log -> "SKIPPED_OVERLAP".equals(log.get("state"))));
    assertEquals(text(run, "id"), text(runtime.automatic(app, binding, "tick-1", Map.of("type", "SCHEDULE"), Map.of()), "id"));
  }

  @Test
  void eventOutboxRollsBackWithDataAndDeleteUsesTheBeforeSnapshot() {
    String record = record("订单A");
    messages.drain(app, 100);
    var flow = publish(draft(Map.of("type", "DATA_EVENT", "formName", "orders", "operations", List.of("UPDATE", "DELETE")), List.of()));
    assertThrows(IllegalStateException.class, () -> transactions.execute(app, () -> {
      records.updateRecord(app, "orders", record, Map.of("name", "回滚"), null, null, null);
      throw new IllegalStateException("rollback");
    }));
    drain();
    assertTrue(instances.findAll(app).isEmpty());
    records.deleteRecord(app, "orders", record, null, null, null);
    messages.drain(app, 100);
    drain();
    assertEquals(1, instances.findAll(app).size());
    var run = instances.findAll(app).getFirst();
    assertEquals("SUCCEEDED", text(state.run(app, text(run, "id")), "status"));
    assertEquals("订单A", object(state.variables(app, run).get("record")).get("name"));
  }

  @Test
  void businessReceiptSurvivesPlatformRollbackAndWorkflowWritesDoNotTrigger() {
    var count = new AtomicInteger();
    assertThrows(IllegalStateException.class, () -> transactions.executePlatform(app, () -> {
      messages.businessEffect(app, "task-1", () -> {
        count.incrementAndGet();
        return record("自动创建");
      });
      throw new IllegalStateException("platform rollback");
    }));
    Object prior = messages.businessEffect(app, "task-1", () -> {
      fail("必须复用已提交的执行回执");
      return null;
    });
    assertNotNull(prior);
    assertEquals(1, count.get());
    messages.drain(app, 100);
    publish(draft(Map.of("type", "DATA_EVENT", "formName", "orders", "operations", List.of("CREATE")), List.of()));
    AppFlowRecordContext.as("root", () -> record("流程内部创建"));
    messages.drain(app, 100);
    drain();
    assertTrue(instances.findAll(app).isEmpty());
  }

  @Test
  void scheduledValidationRejectsStartRecordAndInitiatorRules() {
    var update = node("update", "create", Map.of("form", Map.of("kind", "app", "name", "orders"), "assignments", List.of(Map.of("field", "name", "value", Map.of("kind", "variable", "value", "record.name")))));
    var flow = flows.save(app, null, draft(Map.of("type", "SCHEDULE", "cron", "0 0 9 * * ?", "timezone", "Asia/Shanghai"), List.of(update)));
    assertFalse(flows.validate(app, flow).isEmpty());
  }

  @Test
  void optionalRecordLockIsReleasedWhenAQueuedInstanceIsTerminated() {
    String record = record("锁定订单");
    var flow = publish(draft(Map.of("type", "MANUAL", "formName", "orders", "lockRecord", true), List.of()));
    var run = runtime.manual(app, text(flow, "id"), record, "locked", Map.of());
    assertThrows(jakarta.ws.rs.ClientErrorException.class, () -> records.updateRecord(app, "orders", record, Map.of("name", "不可修改"), null, null, null));
    tasks.manage(app, text(run, "id"), "terminate");
    records.updateRecord(app, "orders", record, Map.of("name", "已解锁"), null, null, null);
    assertEquals("TERMINATED", text(state.run(app, text(run, "id")), "status"));
  }

  io.restassured.specification.RequestSpecification api(String token) {
    return io.restassured.RestAssured.given().header("Authorization", "Bearer " + token).contentType("application/json");
  }

  @Test
  void httpContractScopesAndRetiredRoutes() {
    String token = jwt.sign("admin", java.time.Duration.ofMinutes(5));
    var body = AppFlowModel.api(draft(Map.of("type", "MANUAL", "formName", "orders", "lockRecord", false), List.of()));
    var created = api(token).body(body).post("/apps/" + app + "/flows").then().statusCode(201).extract().jsonPath();
    String flow = created.getString("id");
    assertNotNull(created.getMap("model"));
    assertNull(created.get("flow_model"));
    api(token).post("/apps/" + app + "/flows/" + flow + "/publish").then().statusCode(200);
    String record = record("HTTP 订单");
    var request = Map.of("recordId", record, "requestKey", "http-key");
    var first = api(token).body(request).post("/apps/" + app + "/flows/" + flow + "/instances").then().statusCode(202).extract().path("id");
    assertEquals(first, api(token).body(request).post("/apps/" + app + "/flows/" + flow + "/instances").then().statusCode(202).extract().path("id"));
    api(token).get("/apps/" + app + "/approval-flows").then().statusCode(404);
    api(token).post("/apps/" + app + "/forms/orders/records/" + record + "/approvals").then().statusCode(404);
    var readonly = keys.create(new dev.flexmodel.auth.dto.CreateApiKeyRequest("只读", app, true));
    api(readonly.getKey()).body(Map.of("recordId", record, "requestKey", "read-only"))
      .post("/apps/" + app + "/flows/" + flow + "/instances").then().statusCode(403);
    var other = keys.create(new dev.flexmodel.auth.dto.CreateApiKeyRequest("其他应用", "other-app", false));
    api(other.getKey()).get("/apps/" + app + "/flows").then().statusCode(401);
    var service = keys.create(new dev.flexmodel.auth.dto.CreateApiKeyRequest("流程调用", app, false));
    api(service.getKey()).body(Map.of("recordId", record, "requestKey", "service"))
      .post("/apps/" + app + "/flows/" + flow + "/instances").then().statusCode(202);
    api(token).queryParam("cron", "0 0 9 * * ?").queryParam("timezone", "Asia/Shanghai")
      .get("/apps/" + app + "/flows/cron-preview").then().statusCode(200).body("size()", org.hamcrest.Matchers.is(5));
  }

  @Test
  void browserRoundTripWithQuartzAndRealBackend() throws Exception {
    var script = java.nio.file.Path.of("../flexmodel-apps-ui/e2e/flows-backend.mjs").toAbsolutePath().normalize();
    var output = java.nio.file.Files.createTempFile("flow-browser-", ".log");
    var builder = new ProcessBuilder("node", script.toString()).redirectErrorStream(true).redirectOutput(output.toFile());
    builder.environment().put("FLOW_TEST_API", "http://127.0.0.1:" + io.restassured.RestAssured.port + "/api");
    builder.environment().put("FLOW_TEST_TOKEN", jwt.sign("admin", java.time.Duration.ofMinutes(10)));
    builder.environment().put("FLOW_TEST_APP", app);
    var process = builder.start();
    boolean finished = false;
    try {
      long deadline = System.nanoTime() + java.time.Duration.ofSeconds(180).toNanos();
      while (System.nanoTime() < deadline && !(finished = process.waitFor(100, java.util.concurrent.TimeUnit.MILLISECONDS))) {
        try {
          messages.drain(app, 100);
        } catch (jakarta.ws.rs.ServiceUnavailableException contention) { /* Retry the next polling tick. */ }
      }
      assertTrue(finished, () -> "Browser timeout: " + read(output));
      assertEquals(0, process.exitValue(), () -> read(output));
    } finally {
      if (!finished) process.destroyForcibly();
    }
  }

  private String read(java.nio.file.Path file) {
    try {
      return java.nio.file.Files.readString(file);
    } catch (Exception e) {
      return e.toString();
    }
  }
}
