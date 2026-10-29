package dev.flexmodel.apps;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.apps.dto.AppFormResponse;
import dev.flexmodel.apps.dto.AppTransfer.*;
import dev.flexmodel.common.ProjectUnitOfWork;
import dev.flexmodel.common.SessionContext;
import dev.flexmodel.common.ValidationException;
import dev.flexmodel.common.persistence.ServerSessions;
import dev.flexmodel.data.DataService;
import dev.flexmodel.project.ProjectService;
import dev.flexmodel.session.SessionFactory;
import dev.flexmodel.sql.JdbcSchemaProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;

import java.nio.file.*;
import java.util.*;

import static dev.flexmodel.apps.dto.AppTransfer.*;
import static dev.flexmodel.apps.AppTransferExcel.fail;

@ApplicationScoped
public class AppTransferService {
  @Inject AppTransferExcel excel;
  @Inject AppTransferPlanner planner;
  @Inject AppTransferStore store;
  @Inject AppTransferExecutor executor;
  @Inject AppFormService forms;
  @Inject AppTransferForms transferForms;
  @Inject AppRecordService records;
  @Inject SessionContext context;
  @Inject ProjectService projects;
  @Inject ServerSessions sessions;
  @Inject SessionFactory factory;
  @Inject ProjectUnitOfWork transactions;
  @Inject DataService data;

  String authorize(String appId) {
    if (!context.isAuthenticatedUser() || context.getUserId() == null || context.getUserId().isBlank())
      throw new WebApplicationException("请先登录", 401);
    forms.findForms(appId); // Verify the app using the same boundary as ordinary records.
    if (!(factory.getSchemaProvider(projects.resolveDatabaseName(appId)) instanceof JdbcSchemaProvider))
      fail("首版多表单导入导出仅支持 SQL 数据源");
    return context.getUserId();
  }

  public UploadResponse upload(String appId, Path file) {
    String user = authorize(appId);
    try { if (Files.size(file) > MAX_BYTES) throw new WebApplicationException("文件不能超过 10 MB", 413); }
    catch (java.io.IOException error) { throw new ValidationException("无法读取上传文件", error); }
    WorkbookData workbook = excel.read(file);
    List<SheetMapping> suggestions = workbook.suggestions();
    if (!workbook.platform()) suggestions = suggest(appId, workbook);
    workbook = new WorkbookData(workbook.sheets(), suggestions, workbook.platform());
    String id = UUID.randomUUID().toString();
    long expires = System.currentTimeMillis() + AppTransferStore.TTL;
    store.save(id, ".upload.json", new StagedUpload(appId, user, expires, workbook));
    List<SheetInfo> info = workbook.sheets().stream().map(sheet -> new SheetInfo(sheet.name(), sheet.rows().size(),
      sheet.rows().subList(0, Math.min(100, sheet.rows().size())))).toList();
    return new UploadResponse(id, expires, info, suggestions, workbook.platform());
  }

  private List<SheetMapping> suggest(String appId, WorkbookData workbook) {
    var available = transferForms.all(appId).values().stream().filter(form -> !form.builtin()).toList();
    List<SheetMapping> mappings = new ArrayList<>();
    for (SheetData sheet : workbook.sheets()) {
      var candidates = available.stream().filter(form -> sheet.name().equals(form.name()) || sheet.name().equals(form.label())).toList();
      if (candidates.size() != 1) continue;
      var form = candidates.getFirst();
      List<ColumnMapping> columns = new ArrayList<>();
      Integer key = null;
      List<CellValue> header = sheet.rows().isEmpty() ? List.of() : sheet.rows().getFirst();
      for (int i = 0; i < header.size(); i++) {
        String label = header.get(i).value();
        if ("__key".equals(label)) { key = i; continue; }
        var matches = form.fields().stream().filter(field -> !field.builtin() && (label.equals(field.name()) || label.equals(field.label()))).toList();
        if (matches.size() == 1) columns.add(new ColumnMapping(i, matches.getFirst().name(),
          matches.getFirst().referenceForm() != null ? "EXISTING" : "VALUE", null));
      }
      mappings.add(new SheetMapping(sheet.name(), form.name(), 1, key, columns));
    }
    return mappings;
  }

  public PreviewResponse preview(String appId, String uploadId, PreviewRequest request) {
    String user = authorize(appId);
    StagedUpload upload = store.read(uploadId, ".upload.json", StagedUpload.class);
    store.requireOwner(appId, user, upload.appId(), upload.userId(), upload.expiresAt());
    var available = transferForms.all(appId);
    ImportPlan plan = planner.plan(appId, user, upload.workbook(), request, name -> {
      var form = available.get(name); if (form == null) fail("表单不存在: " + name); return form;
    }, (name, id) -> {
      var target = forms.findForm(appId, name);
      try (var session = sessions.open(target.builtin() ? "system" : projects.resolveDatabaseName(appId))) {
        return session.data().findById(target.name(), id).isPresent();
      }
    });
    store.save(plan.id(), ".plan.json", plan);
    return response(plan);
  }

  PreviewResponse response(ImportPlan plan) {
    return new PreviewResponse(plan.id(), plan.planHash(), plan.expiresAt(), plan.errors().isEmpty() ? "READY" : "INVALID",
      plan.forms(), plan.errors(), plan.rows().subList(0, Math.min(20, plan.rows().size())));
  }

  public List<AppFormResponse> availableForms(String appId) {
    authorize(appId);
    return transferForms.all(appId).values().stream().filter(form -> !form.builtin()).toList();
  }

  public Object status(String appId, String importId) {
    String user = authorize(appId);
    TransferResult completed = executor.receipt(appId, user, importId, null);
    if (completed != null) return completed;
    ImportPlan plan = store.read(importId, ".plan.json", ImportPlan.class);
    store.requireOwner(appId, user, plan.appId(), plan.userId(), plan.expiresAt());
    return response(plan);
  }

  public TransferResult commit(String appId, String importId, CommitRequest request) {
    String user = authorize(appId);
    if (request == null || request.planHash() == null) fail("缺少导入计划摘要");
    TransferResult completed = executor.receipt(appId, user, importId, request.planHash());
    if (completed != null) return completed;
    ImportPlan plan = store.read(importId, ".plan.json", ImportPlan.class);
    store.requireOwner(appId, user, plan.appId(), plan.userId(), plan.expiresAt());
    if (!plan.planHash().equals(request.planHash())) throw new WebApplicationException("导入计划摘要不匹配", 409);
    return executor.commit(plan);
  }

  public byte[] export(String appId, ExportRequest request, boolean template) {
    authorize(appId);
    if (request == null || request.forms() == null || request.forms().isEmpty() || request.forms().size() > MAX_FORMS)
      fail("请选择 1 至 20 个业务表单");
    try (var session = sessions.openSnapshot(projects.resolveDatabaseName(appId))) {
      List<ExportForm> exports = transactions.adopt(appId, session, () -> collect(appId, request, template));
      return excel.write(exports);
    }
  }

  private List<ExportForm> collect(String appId, ExportRequest request, boolean template) {
    Map<String, AppFormResponse> available = new LinkedHashMap<>();
    transferForms.all(appId).values().stream().filter(form -> !form.builtin() && !RECEIPT.equals(form.name())).forEach(form -> available.put(form.name(), form));
    Map<String, LinkedHashMap<String, Map<String, Object>>> collected = new LinkedHashMap<>();
    Map<String, List<String>> selectedFields = new HashMap<>();
    Deque<ExportNode> queue = new ArrayDeque<>();
    int[] total = {0};
    Set<String> roots = new HashSet<>();
    for (var selection : request.forms()) {
      if (selection == null || !roots.add(selection.formName())) fail("目标表单重复");
      var form = requireForm(available, selection.formName());
      collected.put(form.name(), new LinkedHashMap<>());
      selectedFields.put(form.name(), fields(form, selection.fields(), template));
      if (!template) {
        String scope = selection.scope() == null ? "ALL" : selection.scope();
        String filter = switch (scope) {
          case "ALL" -> null;
          case "FILTERED" -> records.buildFilter(form, selection.search(), selection.viewTabId(), null);
          case "IDS" -> {
            if (selection.recordIds() == null || selection.recordIds().isEmpty() || selection.recordIds().size() > MAX_ROWS) fail("请选择导出记录");
            yield JsonUtils.toJsonString(Map.of("id", Map.of("_in", selection.recordIds())));
          }
          default -> throw new ValidationException("导出范围不合法");
        };
        String sort = records.buildSort(form, selection.sortField(), selection.sortOrder());
        for (int page = 1; ; page++) {
          List<Map<String, Object>> rows = data.findRecords(appId, projects.resolveDatabaseName(appId), form.name(), page, 200, filter, stableSort(sort), List.of());
          for (var row : rows) add(collected, queue, form.name(), row, total);
          if (rows.size() < 200) break;
        }
      }
    }
    if (template) {
      boolean changed;
      do {
        changed = false;
        for (String name : List.copyOf(collected.keySet())) {
          var form = requireForm(available, name);
          if (!Boolean.FALSE.equals(request.includeSubforms()) && form.layout() != null && form.layout().items() != null)
            for (var item : form.layout().items()) if ("SUBFORM".equals(item.type()) && !collected.containsKey(item.formName())) {
              var child = requireForm(available, item.formName()); collected.put(child.name(), new LinkedHashMap<>());
              selectedFields.put(child.name(), fields(child, null, true)); changed = true;
              if (collected.size() > MAX_FORMS) fail("关联表单数量超过 20 个");
            }
        }
      } while (changed);
    }
    Set<String> expanded = new HashSet<>();
    while (!queue.isEmpty()) {
      var current = queue.removeFirst();
      var form = requireForm(available, current.form());
      if (current.subforms() && !Boolean.FALSE.equals(request.includeSubforms()) && form.layout() != null && form.layout().items() != null) {
        for (var item : form.layout().items()) {
          if (!"SUBFORM".equals(item.type())) continue;
          var child = requireForm(available, item.formName());
          if (!expanded.add("child/" + current.form() + "/" + current.row().get("id") + "/" + item.formName() + "/" + item.referenceField())) continue;
          String filter = JsonUtils.toJsonString(Map.of(item.referenceField(), Map.of("_eq", current.row().get("id"))));
          collected.computeIfAbsent(child.name(), ignored -> new LinkedHashMap<>());
          for (int page = 1; ; page++) {
            var rows = data.findRecords(appId, projects.resolveDatabaseName(appId), child.name(), page, 200, filter, stableSort("[]"), List.of());
            for (var row : rows) add(collected, queue, child.name(), row, total);
            if (rows.size() < 200) break;
          }
        }
      }
      if (Boolean.TRUE.equals(request.includeReferences())) for (var field : form.fields()) {
        if (field.builtin() || field.referenceForm() == null || current.row().get(field.name()) == null) continue;
        // Only follow exported reference fields, not unrelated hidden fields.
        if (!selectedFields.getOrDefault(form.name(), fields(form, null, false)).contains(field.name())) continue;
        var target = available.get(field.referenceForm());
        if (target == null) continue; // System directories always remain external IDs.
        String id = String.valueOf(current.row().get(field.name()));
        if (!expanded.add("reference/" + target.name() + "/" + id)) continue;
        var row = data.findOneRecord(appId, target.name(), id, List.of());
        if (row == null) fail("导出引用记录已不存在: " + field.label());
        add(collected, queue, target.name(), row, total, false);
      }
    }
    if (collected.size() > MAX_FORMS) fail("关联表单数量超过 20 个");
    List<ExportForm> result = new ArrayList<>();
    for (var entry : collected.entrySet()) {
      var form = requireForm(available, entry.getKey());
      Set<String> names = new LinkedHashSet<>(selectedFields.getOrDefault(form.name(), fields(form, null, template)));
      // A child link must survive even when its reference control is not placed in the child's layout.
      for (String parentName : collected.keySet()) {
        var parent = available.get(parentName);
        if (parent.layout() != null && parent.layout().items() != null) parent.layout().items().stream()
          .filter(item -> "SUBFORM".equals(item.type()) && form.name().equals(item.formName()))
          .forEach(item -> names.add(item.referenceField()));
      }
      result.add(new ExportForm(form, List.copyOf(names), List.copyOf(entry.getValue().values())));
    }
    return result;
  }

  private String stableSort(String sort) {
    List<Map> list = new ArrayList<>(JsonUtils.parseToList(sort, Map.class));
    if (list.stream().noneMatch(item -> "id".equals(item.get("field")))) list.add(Map.of("field", "id", "order", "ASC"));
    return JsonUtils.toJsonString(list);
  }
  private List<String> fields(AppFormResponse form, List<String> requested, boolean template) {
    Set<String> allowed = new LinkedHashSet<>();
    Set<String> exposed = records.exposedFields(form);
    form.fields().stream().filter(field -> !field.builtin() && (template ? AppFieldRules.writable(field, true) : exposed.contains(field.name())))
      .forEach(field -> allowed.add(field.name()));
    if (requested == null) return List.copyOf(allowed);
    if (new HashSet<>(requested).size() != requested.size() || !allowed.containsAll(requested)) fail("导出字段不存在或不在表单可见范围内");
    return requested;
  }
  private AppFormResponse requireForm(Map<String, AppFormResponse> available, String name) {
    var form = available.get(name); if (form == null) fail("业务表单不存在: " + name); return form;
  }
  private void add(Map<String, LinkedHashMap<String, Map<String, Object>>> collected, Deque<ExportNode> queue,
                   String form, Map<String, Object> row, int[] total) { add(collected, queue, form, row, total, true); }
  private void add(Map<String, LinkedHashMap<String, Map<String, Object>>> collected, Deque<ExportNode> queue,
                   String form, Map<String, Object> row, int[] total, boolean subforms) {
    var rows = collected.computeIfAbsent(form, ignored -> new LinkedHashMap<>());
    if (collected.size() > MAX_FORMS) fail("关联表单数量超过 20 个");
    if (rows.putIfAbsent(String.valueOf(row.get("id")), row) == null) {
      if (++total[0] > MAX_ROWS) fail("整包最多导出 5,000 条记录，请缩小范围");
      queue.add(new ExportNode(form, row, subforms));
    } else if (subforms) {
      // Upgrade shared references that are also part of a selected subform tree.
      // collect() expands each child edge once, preventing circular traversal.
      queue.add(new ExportNode(form, row, true));
    }
  }
  private record ExportNode(String form, Map<String, Object> row, boolean subforms) {}
}
