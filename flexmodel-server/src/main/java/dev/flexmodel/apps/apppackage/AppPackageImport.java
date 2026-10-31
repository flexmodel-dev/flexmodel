package dev.flexmodel.apps.apppackage;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.apps.app.AppRepository;
import dev.flexmodel.apps.app.AppService;
import dev.flexmodel.apps.app.dto.AppCreateRequest;
import dev.flexmodel.apps.apppackage.AppPackageValidator.Result;
import dev.flexmodel.apps.apppackage.AppPackageZip.Content;
import dev.flexmodel.apps.approval.AppApprovalFlowService;
import dev.flexmodel.apps.approval.AppApprovalJson;
import dev.flexmodel.apps.dataauthorization.AppDataAuthorizationRepository;
import dev.flexmodel.apps.dataauthorization.AppDataAuthorizationService;
import dev.flexmodel.apps.form.AppFormService;
import dev.flexmodel.apps.form.dto.*;
import dev.flexmodel.apps.navigation.AppNavigationService;
import dev.flexmodel.apps.navigation.dto.AppNavRecord;
import dev.flexmodel.apps.navigation.dto.AppNavResponse;
import dev.flexmodel.apps.navigation.dto.AppNavSaveRequest;
import dev.flexmodel.apps.navigation.dto.NavDestinationRequest;
import dev.flexmodel.apps.record.AppRecordHistoryEvent;
import dev.flexmodel.apps.record.AppRecordHistoryRecorder;
import dev.flexmodel.apps.record.AppRecordService;
import dev.flexmodel.apps.workbench.AppWorkbenchRepository;
import dev.flexmodel.apps.workbench.AppWorkbenchService;
import dev.flexmodel.apps.workbench.dto.WorkbenchComponentRecord;
import dev.flexmodel.apps.workbench.dto.WorkbenchCreateRequest;
import dev.flexmodel.apps.workbench.dto.WorkbenchLayoutRecord;
import dev.flexmodel.apps.workbench.dto.WorkbenchShortcutsConfig;
import dev.flexmodel.codegen.entity.App;
import dev.flexmodel.codegen.entity.Project;
import dev.flexmodel.common.NotFoundException;
import dev.flexmodel.common.ProjectUnitOfWork;
import dev.flexmodel.common.SchemaRegistry;
import dev.flexmodel.common.ValidationException;
import dev.flexmodel.common.persistence.ServerSessions;
import dev.flexmodel.project.ProjectService;
import dev.flexmodel.session.Session;
import dev.flexmodel.session.SessionFactory;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.*;
import java.util.stream.IntStream;

import static dev.flexmodel.apps.apppackage.dto.AppPackage.*;
import static dev.flexmodel.common.Values.*;

/**
 * 整包导入执行器：持久任务状态驱动的补偿式重建（非原子事务）。
 *
 * <p>重建顺序按方案 §5.3：provision → forms → references → layouts → navigation →
 * workbenches → approvals → authorizations → records → activating。每阶段先落盘再执行，
 * 崩溃后由启动扫描依据 phase 与 provisioning 状态决定恢复或补偿，绝不盲目重跑 createApp。</p>
 */
@ApplicationScoped
@Slf4j
public class AppPackageImport {

  @Inject
  AppPackageStore store;
  @Inject
  AppPackageTaskRepository tasks;
  @Inject
  AppPackageZip packages;
  @Inject
  AppPackageValidator validator;
  @Inject
  AppService apps;
  @Inject
  AppFormService forms;
  @Inject
  AppNavigationService navigation;
  @Inject
  AppWorkbenchService appWorkbenchService;
  @Inject
  AppApprovalFlowService approvalService;
  @Inject
  AppDataAuthorizationService authorizationService;
  @Inject
  AppDataAuthorizationRepository authorizationRepository;
  @Inject
  AppRepository appRepository;
  @Inject
  AppWorkbenchRepository appWorkbenchRepository;
  @Inject
  AppRecordService records;
  @Inject
  AppRecordHistoryRecorder history;
  @Inject
  ProjectService projects;
  @Inject
  SchemaRegistry schemaRegistry;
  @Inject
  ProjectUnitOfWork transactions;
  @Inject
  ServerSessions sessions;
  @Inject
  SessionFactory factory;

  private final Object[] locks = IntStream.range(0, 64).mapToObj(ignored -> new Object()).toArray();

  // ---------------------------------------------------------------- 提交入口

  TaskResult commit(StagedPlan plan, String name, String requestDigest) {
    synchronized (lockFor(plan.uploadId())) {
      TaskState existing = tasks.find(plan.uploadId(), plan.userId());
      if (existing != null && AppPackageStore.isActiveStatus(existing.status())) {
        recoverOne(existing);
        existing = tasks.find(plan.uploadId(), plan.userId());
      }
      if (existing != null) {
        if (existing.requestDigest() != null && !existing.requestDigest().equals(requestDigest))
          throw new WebApplicationException("导入请求已变化，请使用首次确认的名称", 409);
        return TaskResult.resultOf(existing);
      }
      TaskState state = new TaskState(plan.uploadId(), plan.userId(), "COMMITTING", "provision", name,
        plan.planHash(), requestDigest, targetProjectId(), null, null, null, System.currentTimeMillis());
      tasks.save(state);
      try {
        return rebuild(plan, state);
      } catch (RuntimeException | Error error) {
        log.warn("App package import {} failed: {}", plan.uploadId(), error.getMessage(), error);
        TaskState latest = tasks.find(plan.uploadId(), plan.userId());
        compensate(latest != null ? latest : state, errorMessage(error));
        TaskState settled = tasks.find(plan.uploadId(), plan.userId());
        return TaskResult.resultOf(settled != null ? settled : state);
      }
    }
  }

  private Object lockFor(String uploadId) {
    return locks[Math.floorMod(uploadId.hashCode(), locks.length)];
  }

  private String targetProjectId() {
    return "app_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
  }

  private String errorMessage(Throwable error) {
    String message = error.getMessage();
    if (message == null || message.isBlank()) message = error.getClass().getSimpleName();
    return message;
  }

  // ---------------------------------------------------------------- 重建

  private TaskResult rebuild(StagedPlan plan, TaskState initial) {
    Content content = readContent(plan);
    Result validation = validator.validate(content);
    if (!validation.valid())
      throw new ValidationException(AppPackageService.formatIssues("包校验失败", validation.issues()));
    String appId = initial.targetProjectId();
    AppService.IMPORT_BYPASS.set(true);
    try {
      Run run = new Run(initial);
      run.phase("provision");
      apps.createApp(new AppCreateRequest(run.state.name(), content.app() == null ? null : content.app().description(),
        appId, content.app() == null ? null : content.app().iconName(),
        content.app() == null ? null : content.app().primaryColor()));
      markProvisioning(appId);
      run.state = run.state.withApp(appId);
      tasks.save(run.state);

      run.phase("forms");
      createSkeletonForms(appId, content.forms());

      run.phase("references");
      createReferenceFields(appId, content.forms());

      run.phase("layouts");
      applyLayouts(appId, content.forms());

      run.phase("navigation");
      Map<String, String> navIds = rebuildNavigation(appId, content.navigation());

      run.phase("workbenches");
      rebuildWorkbenches(appId, content.workbenches(), navIds);

      run.phase("approvals");
      rebuildApprovals(appId, content.approvals());

      run.phase("authorizations");
      rebuildAuthorizations(appId, content.authorizations());

      run.phase("records");
      AppPackageService.RecordStats stats = writeRecords(plan, content, appId);

      TaskResult result = new TaskResult(plan.uploadId(), "COMMITTED", "done", appId, run.state.name(),
        java.time.Instant.now().toString(), stats.total(), stats.forms(), null);
      run.state = run.state.withResult(result);
      tasks.save(run.state);

      run.phase("activating");
      transactions.executePlatform(appId, () -> {
        activate(appId);
        return null;
      });
      run.phase("done");
      tasks.save(run.state.with("COMMITTED", "done", null));
      return run.state.result();
    } finally {
      AppService.IMPORT_BYPASS.remove();
    }
  }

  private Content readContent(StagedPlan plan) {
    try {
      Content content = packages.read(store.readZip(plan.uploadId()));
      if (!packages.planHash(content.rawFiles()).equals(plan.planHash()))
        throw new WebApplicationException("上传包内容已变化，请重新上传", 409);
      return content;
    } catch (IOException error) {
      throw new IllegalStateException("无法读取上传包", error);
    }
  }

  private final class Run {
    TaskState state;

    Run(TaskState state) {
      this.state = state;
    }

    void phase(String phase) {
      state = state.with(state.status(), phase, state.error());
      tasks.save(state);
    }
  }

  // ---------------------------------------------------------------- 表单

  private void createSkeletonForms(String appId, List<AppFormRecord> records) {
    for (AppFormRecord record : records) {
      AppFormFieldRecord nameField = record.fields() == null ? null : record.fields().stream()
        .filter(field -> !field.builtin() && "name".equals(field.name())).findFirst().orElse(null);
      if (nameField != null && !"TEXT".equals(nameField.type()))
        throw new ValidationException("表单 " + record.name() + " 的 name 字段类型必须为 TEXT");
      List<AppFormFieldCreateRequest> fields = record.fields() == null ? List.of() : record.fields().stream()
        .filter(field -> !field.builtin() && !"name".equals(field.name()) && !"REFERENCE".equals(field.type()))
        .map(field -> new AppFormFieldCreateRequest(field.label(), field.name(), field.type(), field.required(),
          field.options(), field.referenceForm(), field.config()))
        .toList();
      forms.createFormWithFields(appId, new AppFormCreateRequest(record.label(), record.name(),
        record.description(), nameField != null), fields);
      if (nameField != null && requiresNameRestore(nameField)) {
        forms.updateField(appId, record.name(), "name", new AppFormFieldUpdateRequest(
          nameField.label(), nameField.required(), null, null, nameField.config()));
      }
    }
  }

  private boolean requiresNameRestore(AppFormFieldRecord nameField) {
    // 骨架字段为「名称」且必填，只能恢复与骨架不同的元数据；required=true 本身不需要变更。
    return !"名称".equals(nameField.label()) || !nameField.required() || nameField.config() != null;
  }

  private void createReferenceFields(String appId, List<AppFormRecord> records) {
    for (AppFormRecord record : records) {
      for (AppFormFieldRecord field : record.fields() == null ? List.<AppFormFieldRecord>of() : record.fields()) {
        if (field.builtin() || "name".equals(field.name()) || !"REFERENCE".equals(field.type())) continue;
        forms.createField(appId, record.name(), new AppFormFieldCreateRequest(field.label(), field.name(),
          field.type(), field.required(), null, field.referenceForm(), field.config()));
      }
    }
  }

  private void applyLayouts(String appId, List<AppFormRecord> records) {
    for (AppFormRecord record : records) {
      if (record.layout() != null && record.layout().items() != null && !record.layout().items().isEmpty())
        forms.saveFormLayout(appId, record.name(), new AppFormLayoutRequest(record.layout().items()));
      if (record.viewTabs() != null && !record.viewTabs().isEmpty())
        forms.patchForm(appId, record.name(), new AppFormPatchRequest(record.viewTabs()));
      String display = record.displayField() == null || record.displayField().isBlank()
        ? null : record.displayField().trim();
      String created = hasNameField(record) ? "name" : null;
      if (!Objects.equals(created, display))
        forms.updateForm(appId, record.name(), new AppFormUpdateRequest(record.label(), record.description(),
          display == null ? "" : display));
    }
  }

  private boolean hasNameField(AppFormRecord record) {
    return record.fields() != null && record.fields().stream()
      .anyMatch(field -> !field.builtin() && "name".equals(field.name()));
  }

  // ---------------------------------------------------------------- 导航与工作台

  private Map<String, String> rebuildNavigation(String appId, List<AppNavRecord> navs) {
    Map<String, String> ids = new LinkedHashMap<>();
    for (AppNavRecord nav : navs == null ? List.<AppNavRecord>of() : navs)
      createNavNode(appId, nav, null, ids);
    return ids;
  }

  private void createNavNode(String appId, AppNavRecord nav, String newParentId, Map<String, String> ids) {
    AppNavResponse created = navigation.createNav(appId, new AppNavSaveRequest(nav.type(), nav.name(), nav.iconName(),
      newParentId, nav.destination() == null ? null
      : new NavDestinationRequest(nav.destination().type(), nav.destination().id()), nav.visibility()));
    ids.put(nav.id(), created.id());
    for (AppNavRecord child : nav.children() == null ? List.<AppNavRecord>of() : nav.children())
      createNavNode(appId, child, created.id(), ids);
  }

  private void rebuildWorkbenches(String appId, List<WorkbenchExport> exports, Map<String, String> navIds) {
    for (WorkbenchExport export : exports == null ? List.<WorkbenchExport>of() : exports) {
      WorkbenchLayoutRecord layout = export.layout() == null
        ? WorkbenchLayoutRecord.empty() : rewriteShortcuts(export.layout(), navIds);
      appWorkbenchService.createWorkbench(appId, new WorkbenchCreateRequest(export.name(), export.description(), layout));
    }
  }

  private WorkbenchLayoutRecord rewriteShortcuts(WorkbenchLayoutRecord layout, Map<String, String> navIds) {
    if (layout == null || layout.components() == null || layout.components().isEmpty()) return layout;
    List<WorkbenchComponentRecord> components = layout.components().stream().map(component -> {
      if (!(component.config() instanceof WorkbenchShortcutsConfig shortcuts)) return component;
      List<String> mapped = (shortcuts.shortcuts() == null ? List.<String>of() : shortcuts.shortcuts()).stream()
        .map(navId -> {
          String target = navIds.get(navId);
          if (target == null) throw new ValidationException("工作台快捷入口导航映射缺失: " + navId);
          return target;
        }).toList();
      return new WorkbenchComponentRecord(component.id(), component.title(), component.placement(),
        new WorkbenchShortcutsConfig(mapped, shortcuts.columnsPerRow()));
    }).toList();
    return new WorkbenchLayoutRecord(layout.schemaVersion(), components);
  }

  // ---------------------------------------------------------------- 审批

  private void rebuildApprovals(String appId, List<Map<String, Object>> approvals) {
    Map<String, Map<String, Object>> created = new LinkedHashMap<>();
    for (Map<String, Object> flow : approvals == null ? List.<Map<String, Object>>of() : approvals) {
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("flowKey", flow.get("flowKey"));
      body.put("name", flow.get("name"));
      body.put("description", flow.get("description"));
      body.put("form", flow.get("form"));
      body.put("definition", flow.get("definition"));
      created.put(text(flow, "id"), approvalService.save(appId, null, body));
    }
    Map<String, String> ids = new LinkedHashMap<>();
    created.forEach((oldId, response) -> ids.put(oldId, text(response, "id")));
    for (Map<String, Object> flow : approvals) {
      Object rewritten = rewriteSubflows(flow.get("definition"), ids);
      if (rewritten == null) continue;
      Map<String, Object> response = created.get(text(flow, "id"));
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("flowKey", response.get("flowKey"));
      body.put("name", response.get("name"));
      body.put("description", response.get("description"));
      body.put("form", response.get("form"));
      body.put("definition", rewritten);
      body.put("revision", response.get("revision"));
      created.put(text(flow, "id"), approvalService.save(appId, text(response, "id"), body));
    }
    for (Map<String, Object> flow : approvals) {
      if ("enabled".equals(text(flow, "status")))
        enableWithChildren(appId, flow, approvals, created, ids, new HashSet<>());
    }
    for (Map<String, Object> flow : approvals) {
      if ("disabled".equals(text(flow, "status")))
        approvalService.status(appId, ids.get(text(flow, "id")), "disabled", revision(created.get(text(flow, "id"))));
    }
  }

  /**
   * 子流程依赖顺序（子先行）发布启用。
   */
  private void enableWithChildren(String appId, Map<String, Object> flow, List<Map<String, Object>> approvals,
                                  Map<String, Map<String, Object>> created, Map<String, String> ids, Set<String> visited) {
    String oldId = text(flow, "id");
    if (!visited.add(oldId)) return;
    for (Map<String, Object> node : AppApprovalJson.flatten(objects(object(flow.get("definition")).get("nodes")))) {
      if (!"subflow".equals(text(node, "type"))) continue;
      String childOldId = text(AppApprovalJson.config(node), "flowId");
      approvals.stream().filter(child -> childOldId.equals(text(child, "id"))).findFirst()
        .ifPresent(child -> enableWithChildren(appId, child, approvals, created, ids, visited));
    }
    approvalService.status(appId, ids.get(oldId), "enabled", revision(created.get(oldId)));
  }

  private Object rewriteSubflows(Object definition, Map<String, String> ids) {
    if (!(definition instanceof Map<?, ?> tree) || !(tree.get("nodes") instanceof List<?> nodes)) return null;
    boolean changed = false;
    List<Object> result = new ArrayList<>();
    for (Object item : nodes) {
      if (item instanceof Map<?, ?> raw) {
        Map<String, Object> node = new LinkedHashMap<>(object(raw));
        Object blocks = rewriteSubflowBlocks(node.get("blocks"), ids);
        if (blocks != null) {
          node.put("blocks", blocks);
          changed = true;
        }
        if ("subflow".equals(text(node, "type"))) {
          String childId = text(AppApprovalJson.config(node), "flowId");
          String mapped = childId.isEmpty() ? null : ids.get(childId);
          if (mapped == null) throw new ValidationException("子流程映射缺失: " + childId);
          Map<String, Object> data = new LinkedHashMap<>(object(node.get("data")));
          Map<String, Object> config = new LinkedHashMap<>(AppApprovalJson.config(node));
          config.put("flowId", mapped);
          data.put("config", config);
          node.put("data", data);
          changed = true;
        }
        result.add(node);
      } else {
        result.add(item);
      }
    }
    return changed ? Map.of("nodes", result) : null;
  }

  private Object rewriteSubflowBlocks(Object blocks, Map<String, String> ids) {
    if (!(blocks instanceof List<?> list)) return null;
    boolean changed = false;
    List<Object> result = new ArrayList<>();
    for (Object item : list) {
      if (item instanceof Map<?, ?> raw) {
        Map<String, Object> branch = new LinkedHashMap<>(object(raw));
        Object nested = rewriteSubflowBlocks(branch.get("blocks"), ids);
        if (nested != null) {
          branch.put("blocks", nested);
          changed = true;
        }
        result.add(branch);
      } else {
        result.add(item);
      }
    }
    return changed ? result : null;
  }

  private int revision(Map<String, Object> response) {
    return ((Number) response.getOrDefault("revision", 1)).intValue();
  }

  // ---------------------------------------------------------------- 数据权限

  private void rebuildAuthorizations(String appId, List<AuthorizationExport> exports) {
    for (AuthorizationExport export : exports == null ? List.<AuthorizationExport>of() : exports) {
      try {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", export.name());
        body.put("enabled", export.enabled());
        body.put("subjects", toMaps(export.subjects()));
        body.put("dataScope", toMaps(export.dataScope()));
        body.put("controls", toMaps(export.controls()));
        authorizationService.save(appId, export.formName(), null, body);
      } catch (ValidationException error) {
        // 数据权限依赖目标实例的目录与表单，单条规则失败不阻断整包导入。
        log.warn("Skip data authorization rule '{}' for app {}: {}", export.name(), appId, error.getMessage());
      }
    }
  }

  private List<Map<String, Object>> toMaps(List<?> values) {
    return values == null || values.isEmpty() ? List.of() : JsonUtils.parseToMapList(JsonUtils.toJsonString(values));
  }

  // ---------------------------------------------------------------- 记录

  private AppPackageService.RecordStats writeRecords(StagedPlan plan, Content content, String appId) {
    if (content.data().isEmpty()) return new AppPackageService.RecordStats(0, List.of());
    String schema = projects.resolveDatabaseName(appId);
    Map<String, AppFormResponse> resolved = forms.findForms(appId).stream()
      .filter(form -> !form.builtin())
      .collect(java.util.stream.Collectors.toMap(AppFormResponse::name, form -> form, (left, right) -> left));
    List<ImportedForm> imported = new ArrayList<>();
    long[] total = {0};
    boolean historyEnabled = history.captureSupported(appId);
    transactions.execute(appId, () -> {
      List<AppRecordHistoryEvent> events = historyEnabled ? new ArrayList<>() : null;
      try (Session session = sessions.open(schema)) {
        for (var entry : content.data().entrySet()) {
          AppFormResponse form = resolved.get(entry.getKey());
          if (form == null) throw new ValidationException("记录数据引用不存在的表单: " + entry.getKey());
          List<Map<String, Object>> rows = entry.getValue();
          for (int start = 0; start < rows.size(); start += 100) {
            List<Map<String, Object>> batch = new ArrayList<>();
            for (int index = start; index < Math.min(start + 100, rows.size()); index++) {
              Map<String, Object> row = rows.get(index);
              Object rowId = row.get("id");
              if (rowId == null) throw new ValidationException("记录缺少 id: " + form.label());
              Map<String, Object> input = new LinkedHashMap<>(row);
              input.remove("id");
              Map<String, Object> values = records.prepareImport(form, input);
              records.assignCreationFields(form, values, plan.userId());
              values.put("id", String.valueOf(rowId));
              batch.add(values);
            }
            try {
              session.data().insertAll(form.name(), batch);
            } catch (RuntimeException error) {
              throw new ValidationException("记录写入失败，整包已回滚: " + form.label(), error);
            }
            if (events != null)
              for (Map<String, Object> values : batch)
                events.add(history.importCreated(appId, plan.uploadId(), form, values, plan.userId()));
          }
          total[0] += rows.size();
          imported.add(new ImportedForm(form.name(), form.label(), rows.size()));
        }
        if (events != null && !events.isEmpty()) history.recordBatch(events);
      }
      return null;
    });
    return new AppPackageService.RecordStats(total[0], List.copyOf(imported));
  }

  // ---------------------------------------------------------------- provisioning

  private void markProvisioning(String appId) {
    App app = requireAppRow(appId);
    app.setProvisioning(AppService.PROVISIONING_IMPORTING);
    appRepository.save(app);
  }

  private void activate(String appId) {
    App app = requireAppRow(appId);
    app.setProvisioning(null);
    appRepository.save(app);
  }

  private App requireAppRow(String appId) {
    App app = appRepository.findApp(appId);
    if (app == null) throw new NotFoundException("应用不存在: " + appId);
    return app;
  }

  // ---------------------------------------------------------------- 补偿与恢复

  void onStart(@Observes StartupEvent event) {
    try {
      recoverPending();
    } catch (RuntimeException error) {
      log.warn("App package import startup recovery failed", error);
    }
  }

  @Scheduled(every = "10m", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
  void retryCleanup() {
    try {
      recoverPending();
    } catch (RuntimeException error) {
      log.warn("App package import cleanup retry failed", error);
    }
  }

  void recoverPending() {
    for (TaskState snapshot : tasks.all()) {
      if (!AppPackageStore.isActiveStatus(snapshot.status()) && !"CLEANUP_REQUIRED".equals(snapshot.status())) continue;
      synchronized (lockFor(snapshot.uploadId())) {
        TaskState state = tasks.findAny(snapshot.uploadId());
        if (state == null) continue;
        if (!AppPackageStore.isActiveStatus(state.status()) && !"CLEANUP_REQUIRED".equals(state.status())) continue;
        try {
          recoverOne(state);
        } catch (RuntimeException error) {
          log.warn("App package import recovery for {} failed: {}", state.uploadId(), error.getMessage());
        }
      }
    }
  }

  /**
   * 单任务恢复决策：不盲目重跑 createApp。
   * <ul>
   *   <li>目标项目不存在 — 未创建或创建中断，清理注册残留后置 FAILED</li>
   *   <li>目标项目导入中（provisioning）— 重建未完成，补偿删除后置 FAILED</li>
   *   <li>目标项目已激活且预写回执存在 — 激活已完成，仅补写 COMMITTED 回执</li>
   * </ul>
   */
  private void recoverOne(TaskState snapshot) {
    TaskState state = snapshot;
    String status = state.status();
    if ("COMMITTING".equals(status)) {
      Project project = state.targetProjectId() == null ? null : projects.findProject(state.targetProjectId());
      if (project == null) {
        cleanupSchemaRemnant(state.targetProjectId());
        tasks.save(state.with("FAILED", state.phase(), message(state, "导入中断，未创建应用")));
      } else if (AppService.isImporting(appRepository.findApp(state.targetProjectId()))) {
        compensate(state, message(state, "导入中断"));
      } else if (state.result() != null) {
        tasks.save(state.with("COMMITTED", "done", null));
      } else {
        compensate(state, message(state, "导入中断"));
      }
      return;
    }
    if ("COMPENSATING".equals(status) || "CLEANUP_REQUIRED".equals(status))
      compensate(state, state.error() == null ? "导入中断" : state.error());
  }

  private String message(TaskState state, String fallback) {
    return state.error() == null || state.error().isBlank() ? fallback : state.error();
  }

  private void compensate(TaskState snapshot, String error) {
    TaskState state = snapshot.with("COMPENSATING", snapshot.phase(), error);
    tasks.save(state);
    try {
      cleanupProject(state.targetProjectId());
      tasks.save(state.with("FAILED", "compensated", error));
      log.info("App package import {} compensated", state.uploadId());
    } catch (RuntimeException | Error cleanupError) {
      log.warn("App package import {} cleanup failed: {}", state.uploadId(), cleanupError.getMessage(), cleanupError);
      tasks.save(state.with("CLEANUP_REQUIRED", state.phase(),
        error + " | 清理失败: " + errorMessage(cleanupError)));
    }
  }

  /**
   * 删除目标应用并核验平台记录、工作台、数据权限、schema 注册确已移除。
   */
  private void cleanupProject(String projectId) {
    if (projectId == null) return;
    if (projects.findProject(projectId) == null) {
      cleanupSchemaRemnant(projectId);
      return;
    }
    apps.deleteApp(projectId);
    if (projects.findProject(projectId) != null)
      throw new IllegalStateException("项目记录清理未完成: " + projectId);
    if (appRepository.findApp(projectId) != null)
      throw new IllegalStateException("应用记录清理未完成: " + projectId);
    if (!appWorkbenchRepository.findByAppId(projectId).isEmpty())
      throw new IllegalStateException("工作台清理未完成: " + projectId);
    if (!authorizationRepository.find(projectId, null).isEmpty())
      throw new IllegalStateException("数据权限规则清理未完成: " + projectId);
    cleanupSchemaRemnant(projectId);
  }

  /**
   * 项目记录不存在时的注册残留清理：createProject 中途失败的分支。
   */
  private void cleanupSchemaRemnant(String projectId) {
    if (projectId == null) return;
    try {
      if (factory.getSchemaProvider(projectId) != null) {
        schemaRegistry.unregisterSchema(projectId);
        schemaRegistry.unregisterModels(projectId);
        log.warn("App package import schema remnant unregistered for {}", projectId);
      }
    } catch (RuntimeException error) {
      log.warn("App package import schema remnant cleanup failed for {}: {}", projectId, error.getMessage());
    }
  }
}
