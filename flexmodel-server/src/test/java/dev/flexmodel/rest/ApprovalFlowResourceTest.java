package dev.flexmodel.rest;

import dev.flexmodel.SQLiteTestResource;
import dev.flexmodel.common.ProjectUnitOfWork;
import dev.flexmodel.common.config.web.jwt.JwtService;
import dev.flexmodel.common.messaging.ReliableMessages;
import dev.flexmodel.common.messaging.RuntimeMessageRepository;
import dev.flexmodel.approval.ApprovalFlowDefinitionRepository;
import dev.flexmodel.approval.ApprovalFlowDeploymentRepository;
import dev.flexmodel.approval.ApprovalInstanceRepository;
import dev.flexmodel.approval.ApprovalRecordLockRepository;
import dev.flexmodel.approval.ApprovalTaskRepository;
import dev.flexmodel.approval.ApprovalWaitRepository;
import dev.flexmodel.notification.NotificationRepository;
import dev.flexmodel.session.SessionFactory;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Duration;
import java.util.*;

import org.junit.jupiter.api.Test;

import static dev.flexmodel.approval.ApprovalJson.*;
import static dev.flexmodel.common.Values.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static dev.flexmodel.query.Expressions.field;

@QuarkusTest
@io.quarkus.test.junit.TestProfile(ApprovalFlowResourceTest.ManualDeliveryProfile.class)
@QuarkusTestResource(SQLiteTestResource.class)
class ApprovalFlowResourceTest {
  public static class ManualDeliveryProfile implements io.quarkus.test.junit.QuarkusTestProfile {
    public Map<String, String> getConfigOverrides() {
      return Map.of("quarkus.scheduler.enabled", "false");
    }
  }

  @ApplicationScoped
  public static class FailingDelivery implements dev.flexmodel.flow.event.DurableFlowListener {
    static volatile boolean failing;

    public void onEvent(String projectId, String eventId, String routingKey, Map<String, Object> event) {
      if (failing && routingKey.equals("flow.usertask.suspended") && text(event, "nodeKey").equals("retry_review"))
        throw new IllegalStateException("模拟消费者短暂故障");
    }
  }

  @Inject
  JwtService jwtService;
  @Inject
  NotificationRepository inbox;
  @Inject
  SessionFactory sessions;
  @Inject
  dev.flexmodel.common.persistence.ServerSessions serverSessions;
  @Inject
  dev.flexmodel.graphql.GraphQLManager graphql;
  @Inject
  ReliableMessages messages;
  @Inject
  ApprovalInstanceRepository approvalInstanceRepository;
  @Inject
  ApprovalTaskRepository approvalTaskRepository;
  @Inject
  ApprovalWaitRepository approvalWaitRepository;
  @Inject
  ApprovalRecordLockRepository approvalRecordLockRepository;
  @Inject
  ApprovalFlowDefinitionRepository approvalFlowDefinitionRepository;
  @Inject
  ApprovalFlowDeploymentRepository approvalFlowDeploymentRepository;
  @Inject
  RuntimeMessageRepository runtimeMessageRepository;
  @Inject
  dev.flexmodel.approval.ApprovalFlowState flowState;
  @Inject
  ProjectUnitOfWork transactions;
  @Inject
  dev.flexmodel.data.DataFmRepository data;
  final String base = "/apps/dev_test";

  RequestSpecification api(String user) {
    return given().header("Authorization", "Bearer " + jwtService.sign(user, Duration.ofMinutes(10))).contentType("application/json");
  }

  String form() {
    String name = "approval_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    api("admin").body(Map.of("name", name, "label", "审批测试", "createDisplayField", true)).post(base + "/forms").then().statusCode(201);
    api("admin").body(Map.of("items", List.of(Map.of("type", "FIELD", "name", "name", "width", "FULL"))))
      .put(base + "/forms/" + name + "/layout").then().statusCode(200);
    return name;
  }

  String record(String form, String name) {
    return api("admin").body(Map.of("name", name)).post(base + "/forms/" + form + "/records").then().statusCode(201).extract().path("id");
  }

  Map<String, Object> node(String id, String kind, Map<String, Object> config) {
    return Map.of("id", id, "type", kind, "data", Map.of("title", id, "config", config));
  }

  Map<String, Object> approval(String id, String mode, List<String> users) {
    return node(id, "approval", Map.of("kind", "approval", "signMode", mode, "rules", List.of(Map.of("id", "rule", "source", "users", "values", users, "scope", "self", "includeChildren", false))));
  }

  Map<String, Object> flow(String form, List<Map<String, Object>> middle) {
    List<Map<String, Object>> nodes = new ArrayList<>();
    nodes.add(node("start", "start", Map.of("kind", "start")));
    nodes.addAll(middle);
    nodes.add(node("end", "end", Map.of("kind", "end")));
    return dev.flexmodel.approval.ApprovalFlowModel.encode(new LinkedHashMap<>(Map.of("name", "真实审批测试", "description", "", "form", Map.of("kind", "app", "name", form), "definition", Map.of("nodes", nodes))));
  }

  Map<String, Object> publish(String form, List<Map<String, Object>> middle) {
    Map<String, Object> created = api("admin").body(flow(form, middle)).post(base + "/approval-flows").then().statusCode(201).extract().as(Map.class);
    return api("admin").body(Map.of("status", 2, "revision", created.get("revision"))).post(base + "/approval-flows/" + created.get("flow_module_id") + "/status").then().statusCode(200).extract().as(Map.class);
  }

  String submit(String form, String record, String flow, String key) {
    return api("admin").body(Map.of("flowId", flow, "requestKey", key)).post(base + "/forms/" + form + "/records/" + record + "/approvals").then().statusCode(202).extract().path("id");
  }

  void drain() {
    for (int i = 0; i < 100; i++) {
      messages.drain("dev_test", 100);
      if (runtimeMessageRepository.findPending("dev_test").isEmpty()) return;
      try {
        Thread.sleep(20);
      } catch (InterruptedException error) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(error);
      }
    }
  }

  List<Map<String, Object>> tasks(String run) {
    return approvalTaskRepository.findByRunId("dev_test", run);
  }

  String task(String run, String user) {
    return tasks(run).stream().filter(t -> text(t, "user_id").equals(user)).findFirst().orElseThrow().get("id").toString();
  }

  void user(String id) {
    try (var session = sessions.createSession()) {
      if (session.data().findById("f_user", id).isEmpty())
        session.data().insert("f_user", new HashMap<>(Map.of("id", id, "name", id, "status", "ACTIVE")));
    }
  }

  void decide(String run, String user, String decision, String key, int status) {
    api(user).body(Map.of("decision", decision, "requestKey", key, "comment", "")).post(base + "/approval-tasks/" + task(run, user) + "/decision").then().statusCode(status);
  }

  @Test
  void durableAllSignaturesLockAndIdempotency() {
    user("approval_peer");
    String form = form(), record = record(form, "原始快照");
    var flow = publish(form, List.of(approval("review", "all", List.of("admin", "approval_peer"))));
    String run = submit(form, record, text(flow, "flow_module_id"), "submit-" + record);
    assertEquals(run, submit(form, record, text(flow, "flow_module_id"), "submit-" + record));
    api("admin").body(Map.of("flowId", flow.get("flow_module_id"), "requestKey", "submit-" + record))
      .post(base + "/forms/" + form + "/records/" + record + "/approvals").then().statusCode(202).body("status", equalTo("SUBMITTING"));
    api("admin").body(Map.of("name", "人工修改")).put(base + "/forms/" + form + "/records/" + record).then().statusCode(409);
    api("admin").delete(base + "/forms/" + form + "/records/" + record).then().statusCode(409);
    drain();
    assertEquals(2, tasks(run).size());
    var state = approvalInstanceRepository.get("dev_test", run);
    api("admin").body(Map.of("taskInstanceId", text(approvalWaitRepository.findByRunId("dev_test", run).getFirst(), "id"))).post("/projects/dev_test/flows/instances/" + text(state, "flow_instance_id") + "/commit").then().statusCode(403);
    api("approval_peer").body(Map.of("decision", "approve", "requestKey", "spoof", "comment", "")).post(base + "/approval-tasks/" + task(run, "admin") + "/decision").then().statusCode(403);
    decide(run, "admin", "approve", "approve-admin", 200);
    decide(run, "admin", "approve", "approve-admin", 200);
    assertEquals("RUNNING", text(flowState.run("dev_test", run), "status"));
    decide(run, "approval_peer", "approve", "approve-peer", 200);
    drain();
    api("admin").get(base + "/approval-instances/" + run).then().statusCode(200).body("status", equalTo("APPROVED")).body("snapshot.name", equalTo("原始快照"));
    assertEquals(2, tasks(run).size());
    api("admin").body(Map.of("name", "审批后修改")).put(base + "/forms/" + form + "/records/" + record).then().statusCode(200);
  }

  @Test
  void notificationsFollowSequentialActivationAndOnlyRootResults() {
    String first = "notice_first_" + UUID.randomUUID().toString().substring(0, 8), second = "notice_second_" + UUID.randomUUID().toString().substring(0, 8);
    user(first);
    user(second);
    String form = form(), record = record(form, "通知顺序");
    var flow = publish(form, List.of(approval("review", "sequential", List.of(first, second))));
    String run = submit(form, record, text(flow, "flow_module_id"), "notify-" + record);
    drain();
    assertEquals(1L, dev.flexmodel.notification.NotificationRepository.number(inbox.state(first), "unread"));
    assertEquals(0L, dev.flexmodel.notification.NotificationRepository.number(inbox.state(second), "unread"));
    decide(run, first, "approve", "first", 200);
    drain();
    assertEquals(1L, dev.flexmodel.notification.NotificationRepository.number(inbox.state(second), "unread"));
    decide(run, second, "reject", "second", 200);
    drain();
    try (var session = sessions.createSession()) {
      assertEquals(1, session.dsl().selectFrom("f_notification").where(field("instance_id").eq(run).and(field("event_key").eq("notification:result:" + run + ":REJECTED"))).count());
    }
    decide(run, second, "reject", "second", 200);
    drain();
    try (var session = sessions.createSession()) {
      assertEquals(1, session.dsl().selectFrom("f_notification").where(field("instance_id").eq(run).and(field("event_key").eq("notification:result:" + run + ":REJECTED"))).count());
    }
  }

  @Test
  void notificationRenderingFailureDoesNotBlockApproval() {
    String form = form(), record = record(form, "变量缺失");
    var rule = Map.of("id", "initiator", "source", "initiator", "scope", "self", "values", List.of(), "includeChildren", false);
    var notice = node("notice", "notification", Map.of("kind", "notification", "rules", List.of(rule), "titleTemplate", "提醒", "contentTemplate", "{{record.missing}}"));
    var flow = publish(form, List.of(notice));
    String run = submit(form, record, text(flow, "flow_module_id"), "missing-" + record);
    drain();
    var detail = api("admin").get(base + "/approval-instances/" + run).then().statusCode(200).body("status", equalTo("APPROVED")).extract().as(Map.class);
    assertTrue(objects(detail.get("logs")).stream().anyMatch(log -> text(object(log.get("output")), "status").equals("failed")));
  }

  @Test
  void sequentialRejectClosesWholeRun() {
    user("approval_peer");
    String form = form(), record = record(form, "顺序");
    var flow = publish(form, List.of(approval("review", "sequential", List.of("admin", "approval_peer"))));
    String run = submit(form, record, text(flow, "flow_module_id"), record);
    drain();
    decide(run, "approval_peer", "approve", "early", 409);
    decide(run, "admin", "approve", "first", 200);
    decide(run, "approval_peer", "reject", "second", 200);
    drain();
    assertEquals("REJECTED", text(approvalInstanceRepository.get("dev_test", run), "status"));
    assertTrue(approvalRecordLockRepository.findByRunId("dev_test", run).isEmpty());
  }

  @Test
  void crudGatewayDefaultAndVersionPinnedSubflow() {
    String form = form(), record = record(form, "源记录");
    var child = publish(form, List.of(approval("child_review", "any", List.of("admin"))));
    Map<String, Object> create = node("create", "create", Map.of("kind", "create", "form", Map.of("kind", "app", "name", form), "assignments", List.of(Map.of("field", "name", "value", Map.of("kind", "literal", "value", "流程生成"))), "conditions", List.of(), "match", "all", "output", "created", "queryMode", "one"));
    Map<String, Object> fallback = new LinkedHashMap<>(node("fallback", "branch", Map.of("kind", "branch", "fallback", true, "match", "all", "conditions", List.of())));
    fallback.put("blocks", List.of(create));
    Map<String, Object> conditional = new LinkedHashMap<>(node("condition", "branch", Map.of("kind", "branch", "fallback", false, "match", "all", "conditions", List.of(Map.of("field", "record.name", "operator", "eq", "value", Map.of("kind", "literal", "value", "不匹配"))))));
    conditional.put("blocks", List.of());
    Map<String, Object> gateway = new LinkedHashMap<>(node("gateway", "gateway", Map.of("kind", "gateway")));
    gateway.put("blocks", List.of(conditional, fallback));
    var parent = publish(form, List.of(gateway, node("sub", "subflow", Map.of("kind", "subflow", "flowId", text(child, "flow_module_id"), "inputs", List.of(), "outputs", List.of()))));
    String run = submit(form, record, text(parent, "flow_module_id"), record);
    drain();
    var children = flowState.children("dev_test", approvalInstanceRepository.get("dev_test", run));
    assertEquals(1, children.size());
    decide(text(children.getFirst(), "id"), "admin", "approve", "child-sign", 200);
    drain();
    assertEquals("APPROVED", text(approvalInstanceRepository.get("dev_test", run), "status"));
    api("admin").queryParam("search", "流程生成").get(base + "/forms/" + form + "/records").then().statusCode(200).body("total", equalTo(1));
    drain();
    api("admin").queryParam("search", "流程生成").get(base + "/forms/" + form + "/records").then().body("total", equalTo(1));
  }

  @Test
  void staleDraftAndUnboundTemplateRejected() {
    String form = form();
    var created = api("admin").body(flow(form, List.of(approval("review", "all", List.of("admin"))))).post(base + "/approval-flows").then().statusCode(201).extract().as(Map.class);
    api("admin").body(created).put(base + "/approval-flows/" + created.get("flow_module_id")).then().statusCode(200);
    api("admin").body(created).put(base + "/approval-flows/" + created.get("flow_module_id")).then().statusCode(409);
    var template = dev.flexmodel.approval.ApprovalFlowModel.decode(flow(form, List.of()));
    template.put("form", Map.of("kind", "demo", "name", "expense"));
    template = dev.flexmodel.approval.ApprovalFlowModel.encode(template);
    var draft = api("admin").body(template).post(base + "/approval-flows").then().statusCode(201).extract().as(Map.class);
    api("admin").body(Map.of("status", 2, "revision", draft.get("revision"))).post(base + "/approval-flows/" + draft.get("flow_module_id") + "/status").then().statusCode(400);
  }

  @Test
  void removedMemberSourceCannotBePublishedOrResolved() {
    var rule = Map.of("id", "removed", "source", "script", "values", List.of(), "scope", "self");
    var body = flow(form(), List.of(node("removed_review", "approval", Map.of("kind", "approval", "signMode", "all", "rules", List.of(rule)))));
    var created = api("admin").body(body).post(base + "/approval-flows").then().statusCode(201).extract().as(Map.class);
    api("admin").body(created).post(base + "/approval-flows/validate").then().statusCode(200)
      .body("nodeId", hasItem("removed_review")).body("message", hasItem("执行人来源或人员范围无效"));
    api("admin").body(Map.of("status", 2, "revision", created.get("revision")))
      .post(base + "/approval-flows/" + text(created, "flow_module_id") + "/status").then().statusCode(400);
    api("admin").body(Map.of("rules", List.of(rule), "initiatorId", "admin", "variables", Map.of()))
      .post(base + "/approval-directory/resolve").then().statusCode(400).body("message", equalTo("执行人来源已不支持，请重新选择"));
  }

  @Test
  void canonicalRecordPersistsAuditAndDoesNotTrustClientIdentity() {
    var body = flow(form(), List.of(approval("review", "all", List.of("admin"))));
    String flowKey = "approval_" + UUID.randomUUID().toString().replace("-", "");
    body.put("flow_key", flowKey);
    body.put("created_by", "spoof");
    body.put("updated_by", "spoof");
    var created = api("admin").body(body).post(base + "/approval-flows").then().statusCode(201)
      .body("flow_model", instanceOf(String.class)).body("status", equalTo(1))
      .body("created_by", equalTo("admin")).body("updated_by", equalTo("admin"))
      .body("archive", equalTo(0)).body("is_deleted", equalTo(0)).body("definition", nullValue())
      .extract().as(Map.class);
    var stored = approvalFlowDefinitionRepository.get("dev_test", text(created, "flow_module_id"));
    assertEquals(created.get("flow_model"), dev.flexmodel.approval.ApprovalFlowModel.encode(dev.flexmodel.approval.ApprovalFlowModel.decode(stored)).get("flow_model"));
    assertEquals(1, dev.flexmodel.approval.ApprovalFlowModel.metadata(stored).get("revision"));
    assertFalse(stored.containsKey("document"));
    api("admin").get(base + "/approval-flows/" + text(created, "flow_module_id")).then().statusCode(200)
      .body("flow_key", equalTo(created.get("flow_key"))).body("flow_model", equalTo(created.get("flow_model")));
    api("admin").body(Map.of("status", 2, "revision", created.get("revision")))
      .post(base + "/approval-flows/" + text(created, "flow_module_id") + "/status").then().statusCode(200).body("flow_key", equalTo(flowKey));
    assertEquals(1, approvalFlowDefinitionRepository.findByFlowKey("dev_test", flowKey).size());
  }


  @Test
  void draftsAndRepeatedPublishesReuseOneNativeDefinitionAndPinRunningVersions() {
    String form = form(), record = record(form, "旧版本记录");
    var created = api("admin").body(flow(form, List.of(approval("old_review", "all", List.of("admin")))))
      .post(base + "/approval-flows").then().statusCode(201).extract().as(Map.class);
    String id = text(created, "flow_module_id");
    assertEquals(id, text(approvalFlowDefinitionRepository.get("dev_test", id), "flow_module_id"));
    assertTrue(approvalFlowDeploymentRepository.findByModuleId("dev_test", id).isEmpty());
    api("admin").body(Map.of("flowName", "绕过审批保存", "flowKey", "bypass", "flowModel", created.get("flow_model")))
      .put("/projects/dev_test/flows/" + id).then().statusCode(403);
    var first = api("admin").body(Map.of("status", 2, "revision", created.get("revision")))
      .post(base + "/approval-flows/" + id + "/status").then().statusCode(200).extract().as(Map.class);
    String firstDeployment = text(dev.flexmodel.approval.ApprovalFlowModel.metadata(approvalFlowDefinitionRepository.get("dev_test", id)), "deploymentId");
    String frozen = text(approvalFlowDeploymentRepository.get("dev_test", firstDeployment), "flow_model");
    String run = submit(form, record, id, record);
    drain();
    assertEquals(firstDeployment, text(approvalInstanceRepository.get("dev_test", run), "flow_deploy_id"));
    var changed = flow(form, List.of(approval("new_review", "any", List.of("admin"))));
    changed.put("revision", first.get("revision"));
    changed.put("flow_name", "新版本审批");
    var saved = api("admin").body(changed).put(base + "/approval-flows/" + id).then().statusCode(200)
      .body("status", equalTo(1)).body("flow_module_id", equalTo(id)).extract().as(Map.class);
    api("admin").body(Map.of("status", 2, "revision", saved.get("revision")))
      .post(base + "/approval-flows/" + id + "/status").then().statusCode(200).body("flow_module_id", equalTo(id));
    String secondDeployment = text(dev.flexmodel.approval.ApprovalFlowModel.metadata(approvalFlowDefinitionRepository.get("dev_test", id)), "deploymentId");
    assertNotEquals(firstDeployment, secondDeployment);
    assertNotNull(approvalFlowDefinitionRepository.find("dev_test", id));
    assertEquals(2, approvalFlowDeploymentRepository.findByModuleId("dev_test", id).size());
    assertEquals(frozen, text(approvalFlowDeploymentRepository.get("dev_test", firstDeployment), "flow_model"));
    api("admin").get(base + "/approval-instances/" + run).then().statusCode(200).body("title", equalTo("真实审批测试"));
    decide(run, "admin", "approve", "old-version-sign", 200);
    drain();
    assertEquals("APPROVED", text(approvalInstanceRepository.get("dev_test", run), "status"));
    String nextRecord = record(form, "新版本记录"), nextRun = submit(form, nextRecord, id, nextRecord);
    drain();
    assertEquals(secondDeployment, text(approvalInstanceRepository.get("dev_test", nextRun), "flow_deploy_id"));
    assertEquals("new_review", text(flowState.waitView("dev_test", approvalWaitRepository.findByRunId("dev_test", nextRun).getFirst()), "node_key"));
    api("admin").delete(base + "/approval-flows/" + id).then().statusCode(204);
    api("admin").get(base + "/approval-flows/" + id).then().statusCode(404);
    decide(nextRun, "admin", "approve", "deleted-definition-sign", 200);
    drain();
    assertEquals("APPROVED", text(approvalInstanceRepository.get("dev_test", nextRun), "status"));
  }

  @Test
  void parentDeploymentKeepsChildVersionAfterChildIsRepublishedAndDisabled() {
    String form = form(), record = record(form, "子流程固定版本");
    var child = publish(form, List.of(approval("child_original", "all", List.of("admin"))));
    String childId = text(child, "flow_module_id");
    String childDeployment = text(dev.flexmodel.approval.ApprovalFlowModel.metadata(approvalFlowDefinitionRepository.get("dev_test", childId)), "deploymentId");
    var parent = publish(form, List.of(approval("before_child", "all", List.of("admin")),
      node("child", "subflow", Map.of("kind", "subflow", "flowId", childId, "inputs", List.of(), "outputs", List.of()))));
    String run = submit(form, record, text(parent, "flow_module_id"), record);
    drain();
    var changed = flow(form, List.of(approval("child_replaced", "all", List.of("admin"))));
    changed.put("revision", child.get("revision"));
    var saved = api("admin").body(changed).put(base + "/approval-flows/" + childId).then().statusCode(200).extract().as(Map.class);
    var published = api("admin").body(Map.of("status", 2, "revision", saved.get("revision")))
      .post(base + "/approval-flows/" + childId + "/status").then().statusCode(200).extract().as(Map.class);
    api("admin").body(Map.of("status", 3, "revision", published.get("revision")))
      .post(base + "/approval-flows/" + childId + "/status").then().statusCode(200);
    decide(run, "admin", "approve", "enter-child", 200);
    drain();
    var childRun = flowState.children("dev_test", approvalInstanceRepository.get("dev_test", run)).getFirst();
    assertEquals(childDeployment, text(childRun, "flow_deploy_id"));
    assertEquals("child_original", text(flowState.waitView("dev_test", approvalWaitRepository.findByRunId("dev_test", text(childRun, "id")).getFirst()), "node_key"));
    decide(text(childRun, "id"), "admin", "approve", "pinned-child-sign", 200);
    drain();
    assertEquals("APPROVED", text(approvalInstanceRepository.get("dev_test", run), "status"));
  }


  @Test
  void delayedMessagesDoNotBlockAnotherApprovalInstance() {
    String form = form();
    var flow = publish(form, List.of(approval("isolated_review", "all", List.of("admin"))));
    String one = record(form, "等待重试"), two = record(form, "可独立推进");
    String blocked = submit(form, one, text(flow, "flow_module_id"), one);
    var start = runtimeMessageRepository.findByMessageId("dev_test", "start:" + blocked).getFirst();
    transactions.execute("dev_test", () -> {
      runtimeMessageRepository.update("dev_test", text(start, "id"), Map.of("available_at", java.time.LocalDateTime.now().plusHours(1)));
      return null;
    });
    String independent = submit(form, two, text(flow, "flow_module_id"), two);
    for (int i = 0; i < 10 && tasks(independent).isEmpty(); i++) messages.drain("dev_test", 100);
    assertEquals(1, tasks(independent).size());
    assertTrue(tasks(blocked).isEmpty());
    transactions.execute("dev_test", () -> {
      runtimeMessageRepository.update("dev_test", text(start, "id"), Map.of("available_at", java.time.LocalDateTime.now()));
      return null;
    });
    drain();
    decide(blocked, "admin", "approve", "unblocked", 200);
    decide(independent, "admin", "approve", "independent", 200);
    drain();
  }

  @Test
  void failedConsumerRollsBackTasksAndRedeliveryDoesNotDuplicateThem() {
    String form = form(), record = record(form, "可靠投递");
    var flow = publish(form, List.of(approval("retry_review", "all", List.of("admin"))));
    FailingDelivery.failing = true;
    String run = submit(form, record, text(flow, "flow_module_id"), record);
    try {
      messages.drain("dev_test", 100);
      assertTrue(tasks(run).isEmpty(), "消费失败必须回滚已经创建的待办");
    } finally {
      FailingDelivery.failing = false;
    }
    var suspended = runtimeMessageRepository.findByType("dev_test", "flow.event").stream()
      .filter(message -> text(object(message.get("payload")), "nodeKey").equals("retry_review") && text(object(object(message.get("payload")).get("variables")), "__runId").equals(run)).findFirst().orElseThrow();
    assertTrue(((Number) suspended.get("attempts")).intValue() >= 1);
    transactions.execute("dev_test", () -> {
      runtimeMessageRepository.update("dev_test", text(suspended, "id"), Map.of("available_at", java.time.LocalDateTime.now()));
      return null;
    });
    drain();
    assertEquals(1, tasks(run).size());
    messages.enqueue("dev_test", "replay:" + run, "flow.event", object(suspended.get("payload")));
    drain();
    assertEquals(1, tasks(run).size());
    decide(run, "admin", "approve", "recover", 200);
    drain();
    assertEquals("APPROVED", text(approvalInstanceRepository.get("dev_test", run), "status"));
  }

  @Test
  void concurrentSubmissionAndSignaturesAdvanceOnlyOnce() {
    user("approval_peer");
    String form = form(), record = record(form, "并发");
    var flow = publish(form, List.of(approval("parallel_review", "all", List.of("admin", "approval_peer"))));
    var first = java.util.concurrent.CompletableFuture.supplyAsync(() -> submit(form, record, text(flow, "flow_module_id"), record));
    var second = java.util.concurrent.CompletableFuture.supplyAsync(() -> submit(form, record, text(flow, "flow_module_id"), record));
    String run = first.join();
    assertEquals(run, second.join());
    drain();
    var a = java.util.concurrent.CompletableFuture.runAsync(() -> decide(run, "admin", "approve", "parallel-a", 200));
    var b = java.util.concurrent.CompletableFuture.runAsync(() -> decide(run, "approval_peer", "approve", "parallel-b", 200));
    java.util.concurrent.CompletableFuture.allOf(a, b).join();
    drain();
    assertEquals("APPROVED", text(approvalInstanceRepository.get("dev_test", run), "status"));
    assertEquals(1, flowState.logs("dev_test", approvalInstanceRepository.get("dev_test", run)).stream().filter(log -> "approval".equals(text(log, "kind"))).count());
  }

  @Test
  void recordActionsUseRealDataAndOnlyOwningFlowCanWriteLockedRecord() {
    String form = form(), record = record(form, "提交快照");
    var create = node("make", "create", recordConfig("create", form, List.of(), "created", List.of(Map.of("field", "name", "value", literal("待变更")))));
    var query = node("find", "query", recordConfig("query", form, List.of(condition("id", variable("created.id"))), "found", List.of()));
    var update = node("change", "update", recordConfig("update", form, List.of(condition("id", variable("found.id"))), "updated", List.of(Map.of("field", "name", "value", literal("已变更")))));
    var delete = node("remove", "delete", recordConfig("delete", form, List.of(condition("id", variable("found.id"))), "deleted", List.of()));
    var own = node("own", "update", recordConfig("update", form, List.of(condition("id", variable("record.id"))), "ownUpdate", List.of(Map.of("field", "name", "value", literal("流程内改写")))));
    var flow = publish(form, List.of(create, query, update, delete, own, approval("review", "all", List.of("admin"))));
    String run = submit(form, record, text(flow, "flow_module_id"), record);
    drain();
    assertEquals(1, tasks(run).size());
    api("admin").get(base + "/forms/" + form + "/records/" + record).then().log().ifValidationFails().statusCode(200).body("name", equalTo("流程内改写"));
    api("admin").body(Map.of("name", "人工写入")).put(base + "/forms/" + form + "/records/" + record).then().statusCode(409);
    var state = approvalInstanceRepository.get("dev_test", run);
    assertEquals("ACTIVE", state.get("status"));
    assertFalse(state.containsKey("variables"));
    var wait = approvalWaitRepository.findByRunId("dev_test", run).getFirst();
    assertFalse(wait.containsKey("variables"));
    assertFalse(wait.containsKey("node_key"));
    assertFalse(wait.containsKey("sign_mode"));
    assertEquals("review", flowState.waitView("dev_test", wait).get("node_key"));
    var nativeLogs = flowState.logs("dev_test", state);
    assertEquals(5, nativeLogs.size());
    var ownLog = nativeLogs.stream().filter(log -> "own".equals(text(log, "node_key"))).findFirst().orElseThrow();
    assertEquals("提交快照", object(object(ownLog.get("input")).get("record")).get("name"));
    assertEquals("提交快照", object(state.get("snapshot")).get("name"));
    assertEquals(1, ((Number) object(flowState.variables("dev_test", state).get("deleted")).get("affectedCount")).intValue());
    api("admin").get(base + "/forms/" + form + "/records").then().body("total", equalTo(1));
    decide(run, "admin", "approve", "record-actions", 200);
    drain();
  }

  Map<String, Object> literal(String value) {
    return Map.of("kind", "literal", "value", value);
  }

  Map<String, Object> variable(String path) {
    return Map.of("kind", "variable", "value", path);
  }

  Map<String, Object> condition(String field, Map<String, Object> binding) {
    return Map.of("field", field, "operator", "eq", "value", binding);
  }

  Map<String, Object> recordConfig(String kind, String form, List<Map<String, Object>> conditions, String output, List<Map<String, Object>> assignments) {
    return Map.of("kind", kind, "form", Map.of("kind", "app", "name", form), "conditions", conditions, "match", "all", "output", output, "queryMode", "one", "assignments", assignments);
  }

  @Test
  void filteredAndBatchLocksPreserveDataAndCallerTransaction() {
    String form = form(), unlocked = record(form, "未锁定"), locked = record(form, "锁定");
    var flow = publish(form, List.of(approval("lock_review", "all", List.of("admin"))));
    String run = submit(form, locked, text(flow, "flow_module_id"), locked);
    drain();
    try (var session = serverSessions.openOwned("dev_test")) {
      assertThrows(jakarta.ws.rs.ClientErrorException.class, () -> session.data().update(form, Map.of("name", "越权"), dev.flexmodel.JsonUtils.toJsonString(Map.of("id", Map.of("_eq", locked)))));
      assertThrows(jakarta.ws.rs.ClientErrorException.class, () -> session.data().delete(form, dev.flexmodel.JsonUtils.toJsonString(Map.of("id", Map.of("_in", List.of(unlocked, locked))))));
      assertThrows(jakarta.ws.rs.ClientErrorException.class, () -> session.data().update(form, Map.of("name", "越权"), field("id").eq(locked)));
      assertThrows(jakarta.ws.rs.ClientErrorException.class, () -> session.data().delete(form, field("id").eq(locked)));
      assertThrows(jakarta.ws.rs.ClientErrorException.class, () -> session.data().deleteAll(form));
      assertThrows(jakarta.ws.rs.ClientErrorException.class, () -> data.updateRecords("dev_test", "dev_test", form, List.of(new HashMap<>(Map.of("id", unlocked, "name", "不应部分提交")), new HashMap<>(Map.of("id", locked, "name", "越权")))));
      assertEquals("未锁定", session.data().findById(form, unlocked).orElseThrow().get("name"));
      session.startTransaction();
      session.data().updateById(form, Map.of("name", "未提交"), unlocked);
      assertTrue(session.isTransactionActive(), "审批拦截器不能提前提交调用者的事务");
      session.rollback();
      assertEquals("未锁定", session.data().findById(form, unlocked).orElseThrow().get("name"));
    }
    decide(run, "admin", "approve", "unlock", 200);
    drain();
  }

  @Test
  void graphqlMutationsCannotBypassRecordLocks() {
    String form = form(), locked = record(form, "审批记录"), unlocked = record(form, "普通记录");
    var flow = publish(form, List.of(approval("graphql_review", "all", List.of("admin"))));
    String run = submit(form, locked, text(flow, "flow_module_id"), locked);
    drain();
    graphql.removeGraphQL("dev_test");
    String camel = dev.flexmodel.codegen.StringUtils.snakeToCamel(form);
    String type = dev.flexmodel.codegen.StringUtils.capitalize(camel);
    api("admin").body(Map.of("query", "query { " + camel + "ById(id:\"" + locked + "\") { id name } }"))
      .post("/projects/dev_test/graphql").then().statusCode(200).body("data." + camel + "ById.id", equalTo(locked));
    String filter = "{id:{_in:[\"" + unlocked + "\",\"" + locked + "\"]}}";
    for (String operation : List.of(
      "update" + type + "ById(id:\"" + locked + "\",set:{name:\"越权\"}){id}",
      "delete" + type + "ById(id:\"" + locked + "\"){id}",
      "update" + type + "(where:" + filter + ",set:{name:\"越权\"}){affectedRows}",
      "delete" + type + "(where:" + filter + "){affectedRows}")) {
      var result = graphql.execute("dev_test", null, "mutation { ...Blocked } fragment Blocked on Mutation { attempt:" + operation + " }", Map.of());
      assertFalse(result.getErrors().isEmpty());
      assertTrue(result.getErrors().stream().anyMatch(error -> error.getMessage().contains("记录正在审批中")), result.getErrors().toString());
    }
    var nested = graphql.execute("dev_test", null, "query { " + camel + "ById(id:\"" + unlocked + "\") { _join_mutation { attempt:delete" + type + "ById(id:\"" + locked + "\"){id} } } }", Map.of());
    assertTrue(nested.getErrors().stream().anyMatch(error -> error.getMessage().contains("记录正在审批中")), nested.getErrors().toString());
    api("admin").get(base + "/forms/" + form + "/records/" + unlocked).then().body("name", equalTo("普通记录"));
    decide(run, "admin", "approve", "graphql-unlock", 200);
    drain();
    var updated = graphql.execute("dev_test", null, "mutation { update" + type + "ById(id:\"" + locked + "\",set:{name:\"已解锁\"}) { id name } }", Map.of());
    assertTrue(updated.getErrors().isEmpty(), updated.getErrors().toString());
    api("admin").get(base + "/forms/" + form + "/records/" + locked).then().body("name", equalTo("已解锁"));
    var created = graphql.execute("dev_test", null, "mutation { created:create" + type + "(data:{name:\"GraphQL新增\"}){id name} }", Map.of());
    assertTrue(created.getErrors().isEmpty(), created.getErrors().toString());
    String createdId = text(object(((Map<String, Object>) created.getData()).get("created")), "id");
    assertFalse(createdId.isBlank());
    String allIds = dev.flexmodel.JsonUtils.toJsonString(List.of(locked, unlocked, createdId));
    var deleted = graphql.execute("dev_test", null, "mutation { delete" + type + "(where:{id:{_in:" + allIds + "}}){affectedRows} }", Map.of());
    assertTrue(deleted.getErrors().isEmpty(), deleted.getErrors().toString());
    api("admin").get(base + "/forms/" + form + "/records").then().body("total", equalTo(0));
  }

  @Test
  void serverSessionsKeepCommittedEventsAndDiscardRolledBackEventsAndCallbacks() {
    String form = form(), record = record(form, "原始值");
    var events = new java.util.concurrent.CopyOnWriteArrayList<dev.flexmodel.event.ChangedEvent>();
    var callbacks = new ArrayList<String>();
    dev.flexmodel.event.EventListener listener = new dev.flexmodel.event.EventListener() {
      @Override
      public void onChanged(dev.flexmodel.event.ChangedEvent event) {
        if (event.getModelName().equals(form)) events.add(event);
      }
    };
    sessions.getEventPublisher().addListener(listener);
    try {
      try (var session = serverSessions.openOwned("dev_test")) {
        session.startTransaction();
        session.data().updateById(form, Map.of("name", "已提交"), record);
        transactions.adopt("dev_test", session, () -> {
          ProjectUnitOfWork.afterCommit(() -> callbacks.add("committed"));
          return null;
        });
        assertTrue(session.isTransactionActive());
        assertTrue(callbacks.isEmpty());
        session.commit();
        session.startTransaction();
        session.data().updateById(form, Map.of("name", "已回滚"), record);
        transactions.adopt("dev_test", session, () -> {
          ProjectUnitOfWork.afterCommit(() -> callbacks.add("rolled-back"));
          return null;
        });
        session.rollback();
        assertTrue(events.isEmpty());
        assertTrue(callbacks.isEmpty());
      }
      assertEquals(List.of("committed"), callbacks);
      assertEquals(1, events.size());
      assertEquals("已提交", text(events.getFirst().getNewData(), "name"));
      try (var session = serverSessions.openOwned("dev_test")) {
        session.startTransaction();
        session.data().updateById(form, Map.of("name", "未提交"), record);
      }
      assertEquals(1, events.size());
      api("admin").get(base + "/forms/" + form + "/records/" + record).then().body("name", equalTo("已提交"));
    } finally {
      sessions.getEventPublisher().removeListener(listener);
    }
  }

  @Test
  void gatewayUsesFirstMatchingBranchAndRejectsSubflowCycles() {
    String form = form(), record = record(form, "匹配");
    List<Map<String, Object>> branches = new ArrayList<>();
    for (String id : List.of("first", "second", "fallback")) {
      var branch = new LinkedHashMap<>(node(id, "branch", Map.of("kind", "branch", "fallback", id.equals("fallback"), "match", "all", "conditions", id.equals("fallback") ? List.of() : List.of(condition("record.name", literal("匹配"))))));
      branch.put("blocks", List.of(approval(id + "_review", "all", List.of("admin"))));
      branches.add(branch);
    }
    var gateway = new LinkedHashMap<>(node("priority_gateway", "gateway", Map.of("kind", "gateway")));
    gateway.put("blocks", branches);
    var published = publish(form, List.of(gateway));
    String run = submit(form, record, text(published, "flow_module_id"), record);
    drain();
    assertEquals("first_review", text(flowState.waitView("dev_test", approvalWaitRepository.findByRunId("dev_test", run).getFirst()), "node_key"));
    decide(run, "admin", "approve", "priority-sign", 200);
    drain();
    var a = api("admin").body(flow(form, List.of())).post(base + "/approval-flows").then().statusCode(201).extract().as(Map.class);
    var bBody = flow(form, List.of(node("to_a", "subflow", Map.of("kind", "subflow", "flowId", text(a, "flow_module_id")))));
    var b = api("admin").body(bBody).post(base + "/approval-flows").then().statusCode(201).extract().as(Map.class);
    var aBody = flow(form, List.of(node("to_b", "subflow", Map.of("kind", "subflow", "flowId", text(b, "flow_module_id")))));
    aBody.put("revision", a.get("revision"));
    var saved = api("admin").body(aBody).put(base + "/approval-flows/" + text(a, "flow_module_id")).then().statusCode(200).extract().as(Map.class);
    api("admin").body(Map.of("status", 2, "revision", saved.get("revision"))).post(base + "/approval-flows/" + text(a, "flow_module_id") + "/status").then().statusCode(400);
  }

  @Test
  void browserRoundTripAgainstRealBackend() throws Exception {
    var script = java.nio.file.Path.of("../flexmodel-apps-ui/e2e/approval-backend.mjs").toAbsolutePath().normalize();
    if (!java.nio.file.Files.exists(script))
      script = java.nio.file.Path.of("flexmodel-apps-ui/e2e/approval-backend.mjs").toAbsolutePath().normalize();
    var outputFile = java.nio.file.Files.createTempFile("approval-browser-", ".log");
    var builder = new ProcessBuilder("node", script.toString()).redirectErrorStream(true).redirectOutput(outputFile.toFile());
    builder.environment().put("APPROVAL_TEST_API", "http://127.0.0.1:" + io.restassured.RestAssured.port);
    builder.environment().put("APPROVAL_TEST_TOKEN", jwtService.sign("admin", Duration.ofMinutes(10)));
    var process = builder.start();
    long deadline = System.nanoTime() + java.time.Duration.ofSeconds(180).toNanos();
    boolean finished = false;
    while (System.nanoTime() < deadline && !(finished = process.waitFor(100, java.util.concurrent.TimeUnit.MILLISECONDS))) {
      try {
        messages.drain("dev_test", 100);
      } catch (jakarta.ws.rs.ServiceUnavailableException busy) { /* SQLite delivery resumes on the next pump. */ }
    }
    if (!finished) {
      process.descendants().forEach(ProcessHandle::destroyForcibly);
      process.destroyForcibly();
    }
    String output = java.nio.file.Files.readString(outputFile);
    java.nio.file.Files.deleteIfExists(outputFile);
    assertTrue(finished, "浏览器验证超时");
    assertEquals(0, process.exitValue(), output);
  }
}
