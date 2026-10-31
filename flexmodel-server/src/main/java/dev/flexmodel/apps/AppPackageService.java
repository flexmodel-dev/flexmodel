package dev.flexmodel.apps;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.flexmodel.JsonUtils;
import dev.flexmodel.apps.AppPackageValidator.Result;
import dev.flexmodel.apps.AppPackageZip.Content;
import dev.flexmodel.apps.approval.AppApprovalFlowService;
import dev.flexmodel.apps.dto.AppFormFieldRecord;
import dev.flexmodel.apps.dto.AppFormRecord;
import dev.flexmodel.apps.dto.AppNavRecord;
import dev.flexmodel.apps.dto.WorkbenchLayoutRecord;
import dev.flexmodel.codegen.entity.AppWorkbench;
import dev.flexmodel.codegen.entity.Project;
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
import org.eclipse.microprofile.config.ConfigProvider;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static dev.flexmodel.apps.dto.AppPackage.*;

/**
 * 应用整包导入导出编排：管理权限校验、只读采集、上传预览与提交分发。
 */
@ApplicationScoped
public class AppPackageService {
  private static final String FLEXMODEL_VERSION = ConfigProvider.getConfig()
    .getOptionalValue("quarkus.application.version", String.class).orElse("dev");
  private static final String DATA_SORT = "[{\"field\":\"id\",\"order\":\"ASC\"}]";

  @Inject
  AppNavigationService navigation;
  @Inject
  AppNavRecordStore navStore;
  @Inject
  AppFormService forms;
  @Inject
  WorkbenchRepository workbenchRepository;
  @Inject
  AppApprovalFlowService approvalFlows;
  @Inject
  AppDataAuthorizationRepository authorizationRepository;
  @Inject
  AppPackageValidator validator;
  @Inject
  AppPackageZip packages;
  @Inject
  AppPackageStore store;
  @Inject
  AppPackageTaskRepository tasks;
  @Inject
  AppPackageImport importer;
  @Inject
  ProjectService projects;
  @Inject
  DataService data;
  @Inject
  ServerSessions sessions;
  @Inject
  SessionFactory factory;
  @Inject
  ProjectUnitOfWork transactions;
  @Inject
  ObjectMapper json;
  @Inject
  SessionContext context;

  /**
   * 已写出的导出文件：资源层流式下载后负责删除临时文件。
   */
  public record ExportedFile(Path file, String fileName, long size) {
  }

  public String requireAuthenticatedUser() {
    if (!context.isAuthenticatedUser() || context.getUserId() == null || context.getUserId().isBlank())
      throw new WebApplicationException("请先登录", 401);
    return context.getUserId();
  }

  // ---------------------------------------------------------------- 导出

  public ExportedFile export(String appId, ExportRequest request) {
    requireAuthenticatedUser();
    if (!navigation.isOwnerOrAdmin(appId))
      throw new WebApplicationException("只有应用所有者或管理员可以导出应用", 403);
    boolean includeData = request != null && Boolean.TRUE.equals(request.includeData());
    Project project = navStore.requireAppProject(appId);
    String schema = projects.resolveDatabaseName(appId);
    if (!(factory.getSchemaProvider(schema) instanceof JdbcSchemaProvider))
      throw new ValidationException("整包导出仅支持 SQL 数据源");

    Structure structure = collectStructure(appId, project);
    String before = structure.digest(json);
    Map<String, List<Map<String, Object>>> dataRows = Map.of();
    if (includeData) {
      try (var session = sessions.openSnapshot(schema)) {
        dataRows = transactions.adopt(appId, session, () -> collectData(appId, schema, structure.formRecords()));
      }
    }
    if (!before.equals(collectStructure(appId, project).digest(json)))
      throw new WebApplicationException("导出采集期间应用配置发生变化，请重试", 409);

    Content content = content(structure, dataRows, includeData);
    // 与导入同源校验，提前暴露导入时会被阻断的问题；数据权限与行级数据问题只算警告。
    Result validation = validator.validate(content);
    if (!validation.valid()) throw new ValidationException(formatIssues("导出内容校验失败", validation.issues()));

    Path staged = store.stageZip();
    long size = write(staged, content);
    return new ExportedFile(staged, downloadName(structure.app().name()), size);
  }

  private Content content(Structure structure, Map<String, List<Map<String, Object>>> dataRows,
                          boolean includeData) {
    return new Content(manifest(structure, dataRows, includeData), structure.app(),
      structure.formRecords(), structure.navigation(), structure.workbenches(), structure.approvals(),
      structure.authorizations(), dataRows, Map.of());
  }

  private long write(Path staged, Content content) {
    try {
      return packages.write(staged, content);
    } catch (IOException error) {
      throw new IllegalStateException("无法写出应用包", error);
    }
  }

  private String downloadName(String appName) {
    String base = (appName == null || appName.isBlank() ? "app" : appName)
      .replaceAll("[\\x00-\\x1f\\\\/:*?\"<>|]", "_");
    return base + EXTENSION;
  }

  private Manifest manifest(Structure structure, Map<String, List<Map<String, Object>>> dataRows,
                            boolean includeData) {
    List<ManifestForm> forms = structure.formRecords().stream()
      .map(record -> new ManifestForm(record.name(),
        record.fields().stream().filter(field -> !field.builtin()).toList().size(),
        includeData,
        includeData ? (long) dataRows.getOrDefault(record.name(), List.of()).size() : null))
      .toList();
    return new Manifest(FORMAT, VERSION, Instant.now().toString(),
      new ManifestSource(structure.project().getId(), FLEXMODEL_VERSION), forms, List.of());
  }

  private Structure collectStructure(String appId, Project project) {
    List<AppFormRecord> formRecords = forms.rawFormRecords(appId);
    List<AppNavRecord> navs = navStore.navRecords(project);
    List<WorkbenchExport> workbenches = workbenchRepository.findByProjectId(project.getId()).stream()
      .map(this::workbenchExport).toList();
    List<Map<String, Object>> approvals = approvalFlows.list(appId).stream()
      .map(flow -> approvalExport(flow, appId)).toList();
    List<AuthorizationExport> authorizations = authorizationRepository.find(appId, null).stream()
      .map(rule -> new AuthorizationExport(rule.formName(), rule.name(), rule.enabled(),
        rule.subjects(), rule.dataScope(), rule.controls()))
      .toList();
    return new Structure(project, appInfo(project), formRecords, navs, workbenches,
      approvals, authorizations);
  }

  private WorkbenchExport workbenchExport(AppWorkbench workbench) {
    WorkbenchLayoutRecord layout = workbench.getLayout() == null
      ? WorkbenchLayoutRecord.empty()
      : JsonUtils.convertValue(workbench.getLayout(), WorkbenchLayoutRecord.class);
    return new WorkbenchExport(workbench.getId(), workbench.getName(), workbench.getDescription(), layout);
  }

  private Map<String, Object> approvalExport(Map<String, Object> flow, String appId) {
    Map<String, Object> export = new LinkedHashMap<>();
    export.put("id", flow.get("id"));
    export.put("flowKey", flow.get("flowKey"));
    export.put("name", flow.get("name"));
    export.put("description", flow.get("description"));
    export.put("status", flow.get("status"));
    export.put("form", flow.get("form"));
    export.put("definition", flow.get("definition"));
    return export;
  }

  @SuppressWarnings("unchecked")
  private AppInfo appInfo(Project project) {
    Map<String, Object> metadata = project.getMetadata() instanceof Map<?, ?> map
      ? (Map<String, Object>) map : Map.of();
    Map<String, Object> app = metadata.get("app") instanceof Map<?, ?> node
      ? (Map<String, Object>) node : Map.of();
    return new AppInfo(project.getName(),
      project.getDescription() == null ? "" : project.getDescription(),
      String.valueOf(app.getOrDefault("iconName", "Boxes")),
      String.valueOf(app.getOrDefault("primaryColor", "blue")));
  }

  private Map<String, List<Map<String, Object>>> collectData(String appId, String schema,
                                                             List<AppFormRecord> formRecords) {
    Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
    List<String> overflow = new ArrayList<>();
    long total = 0;
    for (AppFormRecord record : formRecords) {
      Set<String> businessFields = record.fields().stream()
        .filter(field -> !field.builtin()).map(AppFormFieldRecord::name).collect(Collectors.toSet());
      List<Map<String, Object>> rows = new ArrayList<>();
      for (int page = 1; ; page++) {
        List<Map<String, Object>> found = data.findRecords(appId, schema, record.name(), page, 500, null, DATA_SORT, List.of());
        for (Map<String, Object> row : found) {
          Map<String, Object> clean = new LinkedHashMap<>();
          clean.put("id", String.valueOf(row.get("id")));
          businessFields.forEach(name -> {
            if (row.containsKey(name)) clean.put(name, row.get(name));
          });
          rows.add(clean);
        }
        if (found.size() < 500) break;
      }
      if (rows.size() > MAX_ROWS_PER_FORM) overflow.add(record.name() + ": " + rows.size() + " 行");
      total += rows.size();
      result.put(record.name(), rows);
    }
    if (!overflow.isEmpty())
      throw new ValidationException("导出记录超过每表 " + MAX_ROWS_PER_FORM + " 行上限: " + String.join("，", overflow));
    if (total > MAX_TOTAL_ROWS)
      throw new ValidationException("整包导出记录超过 " + MAX_TOTAL_ROWS + " 行上限，共 " + total + " 行");
    return result;
  }

  private record Structure(Project project, AppInfo app, List<AppFormRecord> formRecords,
                           List<AppNavRecord> navigation, List<WorkbenchExport> workbenches,
                           List<Map<String, Object>> approvals, List<AuthorizationExport> authorizations) {
    String digest(ObjectMapper mapper) {
      try {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("project", List.of(project.getName(), project.getDescription(),
          String.valueOf(project.getUpdatedAt()), String.valueOf(project.getMetadata())));
        snapshot.put("forms", formRecords);
        snapshot.put("navigation", navigation);
        snapshot.put("workbenches", workbenches);
        snapshot.put("approvals", approvals);
        snapshot.put("authorizations", authorizations);
        return sha256(mapper.writeValueAsBytes(snapshot));
      } catch (IOException error) {
        throw new IllegalStateException("无法生成结构摘要", error);
      }
    }
  }

  static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException error) {
      throw new IllegalStateException("生成摘要失败", error);
    }
  }

  // ---------------------------------------------------------------- 上传与预览

  public PreviewResponse upload(String user, Path staged, String fileName) {
    Content content;
    try {
      content = packages.read(staged);
    } catch (IOException error) {
      throw new IllegalStateException("无法读取上传包", error);
    }
    Result validation = validator.validate(content);
    String uploadId = UUID.randomUUID().toString();
    long expiresAt = System.currentTimeMillis() + STORE_TTL_MILLIS;
    long size = fileSize(staged);
    String safeName = safeFileName(fileName);
    PreviewResponse preview = preview(uploadId, content, validation, safeName, size, expiresAt);
    store.publishZip(uploadId, staged);
    store.savePlan(uploadId, new StagedPlan(uploadId, user, expiresAt, packages.planHash(content.rawFiles()),
      safeName, size, preview));
    return preview;
  }

  private long fileSize(Path staged) {
    try {
      return Files.size(staged);
    } catch (IOException error) {
      throw new IllegalStateException("无法读取上传文件", error);
    }
  }

  private String safeFileName(String fileName) {
    String name = fileName == null || fileName.isBlank() ? "package" + EXTENSION : fileName.trim();
    name = name.replaceAll("[\\x00-\\x1f\\\\/:*?\"<>|]", "_");
    if (name.length() > 200) name = name.substring(name.length() - 200);
    return name;
  }

  private PreviewResponse preview(String uploadId, Content content, Result validation, String fileName,
                                  long size, long expiresAt) {
    long totalRecords = content.data().values().stream().mapToLong(List::size).sum();
    int totalFields = content.forms().stream()
      .mapToInt(record -> record.fields().stream().filter(field -> !field.builtin()).toList().size()).sum();
    boolean hasData = !content.data().isEmpty();
    List<FormSummary> forms = content.forms().stream().map(record -> {
      Set<String> layoutFields = new HashSet<>();
      collectLayoutFields(record.layout(), layoutFields);
      int hidden = (int) record.fields().stream()
        .filter(field -> !field.builtin() && !layoutFields.contains(field.name())).count();
      return new FormSummary(record.name(), record.label(),
        record.fields().stream().filter(field -> !field.builtin()).toList().size(),
        content.data().getOrDefault(record.name(), List.of()).size(), hidden);
    }).toList();
    List<ResourceSummary> resources = List.of(
      new ResourceSummary("navigation", "导航", countNav(content.navigation()), "父节点优先重建，节点 ID 重新分配", true),
      new ResourceSummary("workbenches", "工作台", content.workbenches().size(), "工作台 ID 重新生成，快捷入口重写", true),
      new ResourceSummary("approvals", "审批流程", content.approvals().size(), "仅导入托管流程，部署后恢复启用", true),
      new ResourceSummary("authorizations", "数据权限", content.authorizations().size(), "规则 ID 重新生成，校验不通过的规则会跳过", true),
      new ResourceSummary("records", "记录数据", (int) totalRecords, hasData ? "保留业务主键与业务引用" : "包内未包含记录数据", hasData));
    List<String> warnings = new ArrayList<>(validation.warnings());
    if (hasData) warnings.add("附件对象不复制，FILE/IMAGE 等字段保留源 URL，导入后依赖源环境可访问。");
    warnings.add("owner_id / created_by / updated_by 归属导入人，created_at / updated_at 使用导入时间。");
    warnings.add("身份成员按目标目录 ID 校验存在性，请核对两个实例的语义一致性。");
    Manifest manifest = content.manifest();
    PreviewSource source = new PreviewSource(
      manifest.source() == null ? null : manifest.source().appId(),
      content.app() == null ? null : content.app().name(),
      content.app() == null ? null : content.app().description(),
      manifest.exportedAt(), manifest.version() == null ? 0 : manifest.version(),
      manifest.source() == null ? null : manifest.source().flexmodelVersion());
    String defaultAppName = trimTo(content.app() == null || content.app().name() == null
      ? "导入应用" : content.app().name() + "-副本", 100);
    return new PreviewResponse(uploadId, packages.planHash(content.rawFiles()), expiresAt,
      validation.valid() ? "READY" : "INVALID", source,
      new PreviewPackage(fileName, size, hasData, totalRecords, totalFields),
      forms, resources, warnings, validation.issues(), defaultAppName);
  }

  private String trimTo(String value, int maxLength) {
    return value.length() <= maxLength ? value : value.substring(0, maxLength);
  }

  private void collectLayoutFields(dev.flexmodel.apps.dto.AppFormLayoutRecord layout, Set<String> names) {
    if (layout == null || layout.items() == null) return;
    for (var item : layout.items()) {
      if (item != null && "FIELD".equals(item.type()) && item.name() != null) names.add(item.name());
    }
  }

  private int countNav(List<AppNavRecord> navs) {
    int[] count = {0};
    countNav(navs, count);
    return count[0];
  }

  private void countNav(List<AppNavRecord> navs, int[] count) {
    for (AppNavRecord nav : navs == null ? List.<AppNavRecord>of() : navs) {
      count[0]++;
      countNav(nav.children(), count);
    }
  }

  // ---------------------------------------------------------------- 状态与提交

  public Object status(String user, String uploadId) {
    TaskState state = tasks.find(uploadId, user);
    if (state != null) return TaskResult.resultOf(state);
    StagedPlan plan = store.readPlan(uploadId);
    if (!plan.userId().equals(user)) throw new WebApplicationException("导入任务不存在", 404);
    if (System.currentTimeMillis() > plan.expiresAt())
      throw new WebApplicationException("导入预览已过期，请重新上传", 410);
    return plan.preview();
  }

  public TaskResult commit(String user, String uploadId, CommitRequest request) {
    StagedPlan plan = store.planExists(uploadId) ? store.readPlan(uploadId) : null;
    TaskState state = tasks.find(uploadId, user);
    if (plan == null && state == null)
      throw new WebApplicationException("导入预览不存在或已过期，请重新上传", 410);
    if (plan != null && !plan.userId().equals(user))
      throw new WebApplicationException("导入任务不存在", 404);
    if (request == null || request.planHash() == null)
      throw new WebApplicationException("缺少导入计划摘要", 409);
    String expectedHash = plan != null ? plan.planHash() : state.planHash();
    if (!expectedHash.equals(request.planHash()))
      throw new WebApplicationException("导入计划摘要不匹配", 409);
    String name = request.name() != null && !request.name().isBlank() ? request.name().trim()
      : plan != null ? plan.preview().defaultAppName() : state.name();
    if (name == null || name.isBlank()) throw new ValidationException("应用名称不能为空");
    if (name.length() > 100) throw new ValidationException("应用名称长度不能超过100个字符");
    String requestDigest = sha256((name + "\0" + request.planHash()).getBytes(StandardCharsets.UTF_8));
    if (state != null && state.requestDigest() != null && !state.requestDigest().equals(requestDigest))
      throw new WebApplicationException("导入请求已变化，请使用首次确认的名称", 409);
    if (state != null && !AppPackageStore.isActiveStatus(state.status()))
      return TaskResult.resultOf(state);
    if (plan == null || System.currentTimeMillis() > plan.expiresAt())
      throw new WebApplicationException("导入预览已过期，请重新上传", 410);
    return importer.commit(plan, name, requestDigest);
  }

  static String formatIssues(String title, List<ValidationIssue> issues) {
    String detail = issues.stream().limit(20)
      .map(issue -> issue.code() + " " + issue.path() + ": " + issue.message())
      .collect(Collectors.joining("；"));
    return title + "：" + detail;
  }

  record RecordStats(long total, List<ImportedForm> forms) {
  }
}
