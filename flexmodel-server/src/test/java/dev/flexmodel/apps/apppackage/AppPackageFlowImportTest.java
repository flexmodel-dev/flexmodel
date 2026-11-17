package dev.flexmodel.apps.apppackage;

import dev.flexmodel.SQLiteTestResource;
import dev.flexmodel.apps.AppService;
import dev.flexmodel.apps.apppackage.dto.AppPackage.*;
import dev.flexmodel.apps.dto.AppCreateRequest;
import dev.flexmodel.apps.flow.*;
import dev.flexmodel.apps.form.AppFormService;
import dev.flexmodel.apps.form.dto.AppFormCreateRequest;
import dev.flexmodel.apps.record.AppRecordService;
import dev.flexmodel.common.NotFoundException;
import dev.flexmodel.common.SessionContext;
import dev.flexmodel.common.messaging.ReliableMessages;
import dev.flexmodel.common.messaging.RuntimeMessageRepository;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.JobKey;
import org.quartz.Scheduler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static dev.flexmodel.common.Values.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(SQLiteTestResource.class)
@TestProfile(AppPackageFlowImportTest.Profile.class)
class AppPackageFlowImportTest {
  public static class Profile implements QuarkusTestProfile {
    public Map<String, String> getConfigOverrides() {
      return Map.of("quarkus.scheduler.enabled", "false", "flexmodel.project-url-template",
        "jdbc:sqlite:file:" + System.getProperty("java.io.tmpdir").replace('\\', '/')
          + "/flexmodel-test/{{databaseName}}.db?journal_mode=WAL&busy_timeout=5000");
    }
  }

  @Inject
  AppPackageService service;
  @Inject
  AppPackageZip packages;
  @Inject
  AppService apps;
  @Inject
  AppFormService forms;
  @Inject
  AppRecordService records;
  @Inject
  AppFlowService flows;
  @Inject
  AppFlowBindings bindings;
  @Inject
  AppFlowDeploymentRepository deployments;
  @Inject
  AppFlowDefinitionRepository definitions;
  @Inject
  AppFlowInstanceRepository instances;
  @Inject
  AppFlowRuntime runtime;
  @Inject
  AppFlowState state;
  @Inject
  ReliableMessages messages;
  @Inject
  RuntimeMessageRepository outbox;
  @Inject
  Scheduler scheduler;
  @Inject
  SessionContext context;

  @BeforeEach
  void login() {
    context.setAuthenticatedUser(true);
    context.setUserId("admin");
  }

  @Test
  void roundTripRestoresStatesDeploymentsAndRunnableTriggers() throws Exception {
    String source = "pkg" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    TaskResult result = null;
    Path exported = null;
    apps.createApp(new AppCreateRequest("流程导出测试", "", source, "Workflow", "blue"));
    try {
      context.setProjectId(source);
      context.setProjectDatabaseName(source);
      forms.createForm(source, new AppFormCreateRequest("订单", "orders", "", true));
      String record = text(records.createRecord(source, "orders", Map.of("name", "导入订单"), null, null, null), "id");
      var manual = Map.<String, Object>of("type", "MANUAL", "formName", "orders", "lockRecord", false);
      var scheduled = Map.<String, Object>of("type", "SCHEDULE", "cron", "0 0 0 1 1 ? 2099", "timezone", "Asia/Shanghai");
      String child = save(source, "child", manual, List.of());
      flows.publish(source, child);
      String parent = save(source, "parent", manual, List.of(subflow(child)));
      flows.publish(source, parent);
      String stoppedChild = save(source, "stopped_child", manual, List.of());
      flows.publish(source, stoppedChild);
      String stoppedParent = save(source, "stopped_parent", manual, List.of(subflow(stoppedChild)));
      flows.publish(source, stoppedParent);
      flows.status(source, stoppedChild, "disabled");
      flows.status(source, stoppedParent, "disabled");
      save(source, "draft", manual, List.of());
      flows.publish(source, save(source, "event", Map.of("type", "DATA_EVENT", "formName", "orders", "operations", List.of("UPDATE")), List.of()));
      flows.publish(source, save(source, "scheduled", scheduled, List.of()));
      String stoppedSchedule = save(source, "stopped_schedule", scheduled, List.of());
      flows.publish(source, stoppedSchedule);
      flows.status(source, stoppedSchedule, "disabled");
      var originals = byKey(flows.list(source));

      var file = service.export(source, new ExportRequest(true));
      exported = file.file();
      var preview = service.upload("admin", exported, file.fileName());
      assertEquals("READY", preview.status(), preview.issues().toString());
      result = service.commit("admin", preview.uploadId(), new CommitRequest("流程导入测试", preview.planHash()));
      assertEquals("COMMITTED", result.status(), result.error());
      String target = result.appId();
      var imported = byKey(flows.list(target));
      assertEquals(originals.keySet(), imported.keySet());
      for (var entry : originals.entrySet()) {
        var actual = imported.get(entry.getKey());
        String id = text(actual, "id");
        assertNotEquals(text(entry.getValue(), "id"), id);
        assertEquals(entry.getValue().get("status"), actual.get("status"), entry.getKey());
        if ("draft".equals(text(actual, "status"))) {
          assertNull(bindings.find(target, id));
        } else {
          var binding = bindings.find(target, id);
          assertNotNull(binding);
          assertEquals("enabled".equals(text(actual, "status")), binding.getState());
          assertNotEquals(entry.getValue().get("deploymentId"), binding.getDeploymentId());
          assertFalse((Boolean) actual.get("hasDraftChanges"));
        }
      }
      for (String key : List.of("parent", "stopped_parent")) {
        var importedParent = imported.get(key);
        var importedChild = imported.get(key.equals("parent") ? "child" : "stopped_child");
        var call = AppFlowJson.flatten(objects(object(importedParent.get("definition")).get("nodes"))).stream()
          .filter(node -> "subflow".equals(text(node, "type"))).findFirst().orElseThrow();
        assertEquals(importedChild.get("id"), AppFlowJson.config(call).get("flowId"));
        var metadata = AppFlowModel.metadata(deployments.get(target, text(importedParent, "deploymentId")));
        assertEquals(importedChild.get("deploymentId"), object(metadata.get("children")).get("call"));
      }
      assertTrue(instances.findAll(target).isEmpty(), "导入记录不应触发流程");
      var run = runtime.manual(target, text(imported.get("parent"), "id"), record, "import-test", Map.of());
      drain(target);
      assertEquals("SUCCEEDED", text(state.run(target, text(run, "id")), "status"));
      records.updateRecord(target, "orders", record, Map.of("name", "导入后更新"), null, null, null);
      drain(target);
      assertTrue(instances.findAll(target).stream().anyMatch(instance ->
        imported.get("event").get("id").equals(instance.get("flow_id")) && "SUCCEEDED".equals(instance.get("status"))));
      for (String key : List.of("scheduled", "stopped_schedule")) {
        var binding = bindings.find(target, text(imported.get(key), "id"));
        assertEquals(key.equals("scheduled"), scheduler.checkExists(JobKey.jobKey("workflow-" + binding.getId(), "workflow_" + target)));
      }
      assertEquals(target, service.commit("admin", preview.uploadId(), new CommitRequest("流程导入测试", preview.planHash())).appId());
    } finally {
      if (exported != null) Files.deleteIfExists(exported);
      if (result != null) delete(result.appId());
      delete(source);
    }
  }

  @Test
  void failedPublicationCompensatesAppAndLeavesNoTriggerMessages() throws Exception {
    var trigger = Map.<String, Object>of("type", "SCHEDULE", "cron", "0 0 0 1 1 ? 2099");
    var valid = flow("valid", trigger, List.of());
    var invalid = flow("invalid", trigger, List.of(node("notice", "notification", Map.of(
      "titleTemplate", "{{missing.value}}", "contentTemplate", "内容",
      "rules", List.of(Map.of("source", "roles", "scope", "self", "values", List.of("admin")))))));
    valid.put("status", "enabled");
    invalid.put("status", "enabled");
    var content = new AppPackageZip.Content(new Manifest("flexmodel-app-package", 2, "2026-11-16T00:00:00Z",
      new ManifestSource("source", "test"), List.of(), List.of()), new AppInfo("发布失败", "", "Workflow", "blue"),
      List.of(), List.of(), List.of(), List.of(valid, invalid), List.of(), Map.of(), Map.of());
    Path file = Files.createTempFile("flow-import-failure", ".zip");
    TaskResult result = null;
    try {
      packages.write(file, content);
      var preview = service.upload("admin", file, "failure.flexmodel.zip");
      assertEquals("READY", preview.status(), preview.issues().toString());
      result = service.commit("admin", preview.uploadId(), new CommitRequest("发布失败", preview.planHash()));
      assertEquals("FAILED", result.status(), result.error());
      assertTrue(result.error().contains("missing"), result.error());
      String app = result.appId();
      assertThrows(NotFoundException.class, () -> apps.findApp(app));
      assertTrue(definitions.findAllActive(app).isEmpty());
      assertTrue(bindings.active(app).isEmpty());
      assertTrue(outbox.findByType(app, "flow.schedule.sync").isEmpty());
    } finally {
      Files.deleteIfExists(file);
      if (result != null) delete(result.appId());
    }
  }

  private Map<String, Map<String, Object>> byKey(List<Map<String, Object>> values) {
    return values.stream().collect(Collectors.toMap(flow -> text(flow, "flowKey"), flow -> flow));
  }

  private String save(String app, String key, Map<String, Object> trigger, List<Map<String, Object>> tasks) {
    return text(flows.save(app, null, flow(key, trigger, tasks)), "id");
  }

  private Map<String, Object> flow(String key, Map<String, Object> trigger, List<Map<String, Object>> tasks) {
    var nodes = new ArrayList<Map<String, Object>>();
    nodes.add(node("start", "start", Map.of("trigger", trigger)));
    nodes.addAll(tasks);
    nodes.add(node("end", "end", Map.of()));
    return new LinkedHashMap<>(Map.of("id", key, "flowKey", key, "name", key, "description", "",
      "status", "draft", "definition", Map.of("nodes", nodes)));
  }

  private Map<String, Object> node(String id, String kind, Map<String, Object> config) {
    var cfg = new LinkedHashMap<>(config);
    cfg.put("kind", kind);
    return Map.of("id", id, "type", kind, "data", Map.of("title", id, "config", cfg));
  }

  private Map<String, Object> subflow(String child) {
    return node("call", "subflow", Map.of("flowId", child, "inputs", List.of(), "outputs", List.of()));
  }

  private void drain(String app) {
    for (int i = 0; i < 20; i++) if (messages.drain(app, 100) == 0) break;
  }

  private void delete(String app) {
    if (app == null) return;
    try {
      apps.deleteApp(app);
    } catch (NotFoundException ignored) {
      // 失败导入已补偿删除。
    }
  }
}
