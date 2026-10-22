package dev.flexmodel.mcp;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.SQLiteTestResource;
import dev.flexmodel.approval.ApprovalFlowModel;
import dev.flexmodel.approval.ApprovalFlowService;
import dev.flexmodel.approval.ApprovalRepository;
import dev.flexmodel.apps.AppFormService;
import dev.flexmodel.auth.dto.CreateApiKeyRequest;
import dev.flexmodel.auth.service.ApiKeyService;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises discovery, authentication, authoring and publication through the MCP transport.
 */
@QuarkusTest
@QuarkusTestResource(SQLiteTestResource.class)
class AppApprovalToolsTest {
  @Inject
  ApiKeyService keys;
  @Inject
  AppTools apps;
  @Inject
  ApprovalFlowService flows;
  @Inject
  ApprovalRepository repository;
  @Inject
  AppFormService forms;

  private final List<String> keyIds = new ArrayList<>();
  private final List<String> flowIds = new ArrayList<>();
  private String key;
  private String keyId;
  private String session;
  private String form;
  private int requestId;

  @BeforeEach
  void setup() {
    var credential = keys.create(new CreateApiKeyRequest("approval-mcp-test", "dev_test", false));
    keyIds.add(credential.getId());
    key = credential.getKey();
    keyId = credential.getId();
    var initialized = given().queryParam("api_key", key).contentType("application/json")
      .accept("application/json, text/event-stream")
      .body(Map.of("jsonrpc", "2.0", "id", ++requestId, "method", "initialize", "params", Map.of(
        "protocolVersion", "2025-03-26", "capabilities", Map.of(),
        "clientInfo", Map.of("name", "approval-tools-test", "version", "1"))))
      .post("/mcp").then().statusCode(200).extract().response();
    session = initialized.header("Mcp-Session-Id");
    assertNotNull(session);
    rpc("notifications/initialized", Map.of(), true);
    form = "mcp_approval_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    assertFalse(apps.create_app_form("dev_test", "MCP 审批", form, "", true, "[]").startsWith("Error:"));
  }

  @AfterEach
  void cleanup() {
    for (String id : flowIds) flows.delete("dev_test", id);
    if (form != null) {
      forms.deleteForm("dev_test", form);
    }
    keyIds.forEach(keys::delete);
  }

  @Test
  void discoversToolsAndCreatesValidatesPublishesAndDisablesFlow() {
    var listed = rpc("tools/list", Map.of(), false);
    List<Map<String, Object>> tools = objects(object(listed.get("result")).get("tools"));
    for (String name : List.of("get_directory", "resolve_members", "get_app_approval_capabilities",
      "list_app_approval_flows", "get_app_approval_flow", "save_app_approval_flow",
      "validate_app_approval_flow", "set_app_approval_flow_status")) {
      assertTrue(tools.stream().anyMatch(tool -> name.equals(tool.get("name"))), name);
    }
    var saveSchema = object(tools.stream().filter(tool -> "save_app_approval_flow".equals(tool.get("name"))).findFirst().orElseThrow().get("inputSchema"));
    assertFalse(((List<?>) saveSchema.get("required")).contains("flowId"));

    var capabilities = JsonUtils.parseToMap(call("get_app_approval_capabilities", Map.of("appId", "dev_test")));
    assertEquals(1, capabilities.get("schemaVersion"));
    assertTrue(objects(capabilities.get("forms")).stream().anyMatch(item -> form.equals(item.get("name"))));
    for (Object sample : object(capabilities.get("examples")).values()) {
      var body = bindForm(object(sample));
      assertEquals("[]", call("validate_app_approval_flow", Map.of("appId", "dev_test", "flowJson", JsonUtils.toJsonString(body))));
    }
    for (String kind : List.of("create", "update", "query", "delete")) {
      var config = object(object(capabilities.get("nodeConfigs")).get(kind));
      assertTrue(config.keySet().containsAll(List.of("form", "conditions", "assignments", "output", "queryMode", "match")));
      config.put("form", Map.of("kind", "app", "name", form));
      var view = ApprovalFlowModel.decode(bindForm(object(object(capabilities.get("examples")).get("simple"))));
      var nodes = new ArrayList<>(objects(object(view.get("definition")).get("nodes")));
      nodes.set(1, Map.of("id", "data_task", "type", kind, "data", Map.of("title", kind, "config", config)));
      view.put("definition", Map.of("nodes", nodes));
      assertEquals("[]", call("validate_app_approval_flow", Map.of("appId", "dev_test", "flowJson", JsonUtils.toJsonString(ApprovalFlowModel.encode(view)))));
    }
    var draft = createDraft(object(object(capabilities.get("examples")).get("simple")));
    String id = draft.get("flow_module_id").toString();
    assertEquals(1, draft.get("status"));
    assertEquals("api_key:" + keyId, draft.get("created_by"));
    assertEquals(draft, JsonUtils.parseToMap(call("get_app_approval_flow", Map.of("appId", "dev_test", "flowId", id))));
    assertTrue(JsonUtils.parseToMapList(call("list_app_approval_flows", Map.of("appId", "dev_test")))
      .stream().anyMatch(item -> id.equals(item.get("flow_module_id"))));

    var published = status(id, 2, draft);
    assertEquals(2, published.get("status"));
    assertTrue(call("set_app_approval_flow_status", Map.of("appId", "dev_test", "flowId", id,
      "status", 3, "revision", draft.get("revision"))).startsWith("Error:"));
    var edited = new LinkedHashMap<>(published);
    edited.put("flow_name", "修改后的审批");
    var saved = JsonUtils.parseToMap(call("save_app_approval_flow", Map.of("appId", "dev_test", "flowId", id, "flowJson", JsonUtils.toJsonString(edited))));
    assertEquals(1, saved.get("status"));
    assertEquals("修改后的审批", saved.get("flow_name"));
    assertTrue(call("save_app_approval_flow", Map.of("appId", "dev_test", "flowId", id, "flowJson", JsonUtils.toJsonString(edited))).startsWith("Error:"));
    var republished = status(id, 2, saved);
    var disabled = status(id, 3, republished);
    assertEquals(3, ((Number) repository.definition("dev_test", id).get("status")).intValue(), "persisted native Flow status");
    assertEquals(3, disabled.get("status"));
    assertEquals(3, JsonUtils.parseToMap(call("get_app_approval_flow", Map.of("appId", "dev_test", "flowId", id))).get("status"));
  }

  @Test
  void keepsInvalidDraftButRejectsPublishingAndMalformedModels() {
    var capabilities = JsonUtils.parseToMap(call("get_app_approval_capabilities", Map.of("appId", "dev_test")));
    var body = bindForm(object(object(capabilities.get("examples")).get("simple")));
    var view = ApprovalFlowModel.decode(body);
    var nodes = objects(object(view.get("definition")).get("nodes"));
    object(object(nodes.get(1).get("data")).get("config")).put("rules", List.of());
    var draft = createDraft(ApprovalFlowModel.encode(view));
    String id = draft.get("flow_module_id").toString();
    var issues = JsonUtils.parseToMapList(call("validate_app_approval_flow", Map.of("appId", "dev_test", "flowJson", JsonUtils.toJsonString(draft))));
    assertTrue(issues.stream().anyMatch(issue -> "review".equals(issue.get("nodeId"))));
    assertTrue(call("set_app_approval_flow_status", Map.of("appId", "dev_test", "flowId", id, "status", 2, "revision", draft.get("revision"))).startsWith("Error:"));
    assertTrue(call("set_app_approval_flow_status", Map.of("appId", "dev_test", "flowId", id, "status", 1, "revision", draft.get("revision"))).startsWith("Error:"));
    assertTrue(call("save_app_approval_flow", Map.of("appId", "dev_test", "flowJson", JsonUtils.toJsonString(draft))).startsWith("Error:"));
    assertTrue(call("save_app_approval_flow", Map.of("appId", "dev_test", "flowJson", "{broken")).startsWith("Error:"));
    assertTrue(call("validate_app_approval_flow", Map.of("appId", "dev_test", "flowJson", "{\"flow_model\":\"{}\"}")).startsWith("Error:"));
    assertEquals(1, JsonUtils.parseToMap(call("get_app_approval_flow", Map.of("appId", "dev_test", "flowId", id))).get("status"));
  }

  @Test
  void exposesGeneralDirectoryAndResolvesMembersWithoutLeakingCredentials() {
    var directory = JsonUtils.parseToMap(call("get_directory", Map.of()));
    var members = objects(directory.get("members"));
    assertTrue(members.stream().anyMatch(member -> "admin".equals(member.get("id"))));
    assertTrue(directory.containsKey("roles"));
    assertTrue(directory.containsKey("organizations"));
    for (var member : members)
      assertEquals(java.util.Set.of("id", "name", "roleIds", "organizationIds", "role", "organization", "managerId"), member.keySet());
    assertEquals("[\"admin\"]", call("resolve_members", Map.of("rulesJson", """
      [{"source":"users","values":["admin","admin","missing"],"scope":"self"}]
      """)));
    assertEquals("[\"admin\"]", call("resolve_members", Map.of("initiatorId", "admin", "rulesJson", """
      [{"source":"initiator","values":[],"scope":"self"}]
      """)));
    assertEquals("[\"admin\"]", call("resolve_members", Map.of("rulesJson", """
      [{"source":"variable","values":["reviewers"],"scope":"self"}]
      """, "variablesJson", "{\"reviewers\":[\"admin\"]}")));
    assertTrue(call("resolve_members", Map.of("rulesJson", "[{\"source\":\"script\",\"scope\":\"self\"}]")).startsWith("Error:"));
  }

  @Test
  void enforcesProjectAndReadOnlyKeysOnEveryRequestIncludingReusedSessions() {
    var body = object(object(JsonUtils.parseToMap(call("get_app_approval_capabilities", Map.of("appId", "dev_test"))).get("examples")).get("simple"));
    var draft = createDraft(body);
    String id = draft.get("flow_module_id").toString();
    var readonly = keys.create(new CreateApiKeyRequest("read-only", "dev_test", true));
    keyIds.add(readonly.getId());
    key = readonly.getKey();
    assertFalse(call("get_app_approval_flow", Map.of("appId", "dev_test", "flowId", id)).startsWith("Error:"));
    assertFalse(call("get_directory", Map.of()).startsWith("Error:"));
    assertTrue(call("save_app_approval_flow", Map.of("appId", "dev_test", "flowJson", JsonUtils.toJsonString(bindForm(body)))).contains("Read-only"));
    assertTrue(call("set_app_approval_flow_status", Map.of("appId", "dev_test", "flowId", id, "status", 2, "revision", draft.get("revision"))).contains("Read-only"));
    assertTrue(call("list_app_approval_flows", Map.of("appId", "different_app")).contains("cannot access"));
    assertTrue(call("get_app_approval_capabilities", Map.of("appId", "different_app")).contains("cannot access"));
    given().contentType("application/json").accept("application/json, text/event-stream")
      .body(Map.of("jsonrpc", "2.0", "id", 99, "method", "tools/list")).post("/mcp").then().statusCode(401);
  }

  private Map<String, Object> createDraft(Map<String, Object> source) {
    var saved = JsonUtils.parseToMap(call("save_app_approval_flow", Map.of("appId", "dev_test", "flowJson", JsonUtils.toJsonString(bindForm(source)))));
    flowIds.add(saved.get("flow_module_id").toString());
    return saved;
  }

  private Map<String, Object> bindForm(Map<String, Object> source) {
    var view = ApprovalFlowModel.decode(source);
    view.put("form", Map.of("kind", "app", "name", form));
    return ApprovalFlowModel.encode(view);
  }

  private Map<String, Object> status(String id, int value, Map<String, Object> previous) {
    return JsonUtils.parseToMap(call("set_app_approval_flow_status", Map.of("appId", "dev_test", "flowId", id, "status", value, "revision", previous.get("revision"))));
  }

  private String call(String name, Map<String, Object> arguments) {
    var response = rpc("tools/call", Map.of("name", name, "arguments", arguments), false);
    assertFalse(response.containsKey("error"), () -> JsonUtils.toJsonString(response));
    return objects(object(response.get("result")).get("content")).getFirst().get("text").toString();
  }

  private Map<String, Object> rpc(String method, Map<String, Object> params, boolean notification) {
    var body = new LinkedHashMap<String, Object>();
    body.put("jsonrpc", "2.0");
    body.put("method", method);
    body.put("params", params);
    if (!notification) body.put("id", ++requestId);
    var response = given().queryParam("api_key", key).header("Mcp-Session-Id", session)
      .header("MCP-Protocol-Version", "2025-03-26").contentType("application/json")
      .accept("application/json, text/event-stream").body(body).post("/mcp");
    response.then().statusCode(notification ? 202 : 200);
    return notification ? Map.of() : JsonUtils.parseToMap(response.asString());
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> object(Object value) {
    return (Map<String, Object>) value;
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> objects(Object value) {
    return (List<Map<String, Object>>) value;
  }
}
