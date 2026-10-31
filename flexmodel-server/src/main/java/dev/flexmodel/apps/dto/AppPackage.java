package dev.flexmodel.apps.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Duration;
import java.util.List;

/**
 * 应用整包导入导出协议（.flexmodel.zip）与 REST 契约。
 *
 * @see docs/plans/app-import-export.md
 */
public final class AppPackage {
  private AppPackage() {
  }

  public static final String FORMAT = "flexmodel-app-package";
  public static final int VERSION = 1;
  public static final String EXTENSION = ".flexmodel.zip";

  // 包级安全限额：导出与导入一致执行
  public static final long MAX_PACKAGE_BYTES = 50L * 1024 * 1024;
  public static final long MAX_UNCOMPRESSED_BYTES = 100L * 1024 * 1024;
  public static final long MAX_ENTRY_BYTES = 50L * 1024 * 1024;
  public static final int MAX_ENTRIES = 128;
  public static final int MAX_JSON_DEPTH = 64;

  // 结构限额
  public static final int MAX_FORMS = 20;
  public static final int MAX_FIELDS_PER_FORM = 500;
  public static final int MAX_LAYOUT_ITEMS = 500;
  public static final int MAX_VIEW_TABS = 20;
  public static final int MAX_NAV_NODES = 1000;
  public static final int MAX_NAV_DEPTH = 16;
  public static final int MAX_WORKBENCHES = 50;
  public static final int MAX_WORKBENCH_COMPONENTS = 100;
  public static final int MAX_APPROVAL_NODES = 200;
  public static final int MAX_APPROVAL_FLOWS = 100;
  public static final int MAX_RULES_PER_APP = 500;

  // 记录限额
  public static final int MAX_ROWS_PER_FORM = 5000;
  public static final int MAX_TOTAL_ROWS = 5000;

  // 暂存与回执保留
  public static final long STORE_TTL_MILLIS = Duration.ofMinutes(30).toMillis();
  public static final long TASK_TTL_MILLIS = Duration.ofDays(7).toMillis();

  // 包内固定文件名
  public static final String MANIFEST_FILE = "manifest.json";
  public static final String APP_FILE = "app.json";
  public static final String FORMS_FILE = "forms.json";
  public static final String NAVIGATION_FILE = "navigation.json";
  public static final String WORKBENCHES_FILE = "workbenches.json";
  public static final String APPROVALS_FILE = "approvals.json";
  public static final String AUTHORIZATIONS_FILE = "data-authorizations.json";
  public static final String DATA_DIRECTORY = "data/";

  public record Manifest(String format, Integer version, String exportedAt,
                         ManifestSource source, List<ManifestForm> forms, List<String> warnings) {
  }

  public record ManifestSource(String appId, String flexmodelVersion) {
  }

  public record ManifestForm(String name, Integer fieldCount, Boolean dataIncluded, Long dataRows) {
  }

  public record AppInfo(String name, String description, String iconName, String primaryColor) {
  }

  public record DataFile(List<java.util.Map<String, Object>> rows) {
  }

  public record WorkbenchExport(String id, String name, String description, WorkbenchLayoutRecord layout) {
  }

  public record AuthorizationExport(String formName, String name, Boolean enabled,
                                    List<AppDataAuthorization.Selection> subjects,
                                    List<AppDataAuthorization.Selection> dataScope,
                                    List<AppDataAuthorization.Control> controls) {
  }

  public record ExportRequest(Boolean includeData) {
  }

  public record CommitRequest(String name, String planHash) {
  }

  public record ValidationIssue(String code, String path, String message) {
  }

  public record FormSummary(String name, String label, int fieldCount, long dataRows, int hiddenFieldCount) {
  }

  public record ResourceSummary(String key, String label, int count, String detail, boolean included) {
  }

  public record PreviewSource(String appId, String appName, String description, String exportedAt,
                              int formatVersion, String flexmodelVersion) {
  }

  public record PreviewPackage(String fileName, long size, boolean hasData, long totalRecords, int totalFields) {
  }

  public record PreviewResponse(String uploadId, String planHash, long expiresAt, String status,
                                PreviewSource source, @JsonProperty("package") PreviewPackage pkg,
                                List<FormSummary> forms, List<ResourceSummary> resources,
                                List<String> warnings, List<ValidationIssue> issues, String defaultAppName) {
  }

  public record ImportedForm(String name, String label, long count) {
  }

  /**
   * 提交执行结果：COMMITTED 携带最终 appId；FAILED / CLEANUP_REQUIRED 携带 error。
   */
  public record TaskResult(String uploadId, String status, String appId, String appName, String importedAt,
                           long totalRecords, List<ImportedForm> forms, String error) {
    public static TaskResult resultOf(TaskState state) {
      if (state.result() != null) return state.result();
      return new TaskResult(state.uploadId(), state.status(), state.appId(), state.name(),
        java.time.Instant.ofEpochMilli(state.updatedAt()).toString(), 0, List.of(), state.error());
    }
  }

  /**
   * 暂存计划：绑定用户、过期时间、计划摘要与预览。
   */
  public record StagedPlan(String uploadId, String userId, long expiresAt, String planHash,
                           String fileName, long size, PreviewResponse preview) {
  }

  /**
   * 持久任务状态与回执。status: COMMITTING / COMPENSATING / COMMITTED / FAILED / CLEANUP_REQUIRED。
   * phase 记录重建进度，用于中断恢复决策；requestDigest 固化首次确认的规范化请求摘要。
   * result 在激活前预写，用于进程中断后恢复 COMMITTED 回执。
   */
  public record TaskState(String uploadId, String userId, String status, String phase, String name,
                          String planHash, String requestDigest, String targetProjectId, String appId,
                          String error, TaskResult result, long updatedAt) {
    public TaskState with(String status, String phase, String error) {
      return new TaskState(uploadId, userId, status, phase, name, planHash, requestDigest,
        targetProjectId, appId, error, result, System.currentTimeMillis());
    }

    public TaskState withApp(String appId) {
      return new TaskState(uploadId, userId, status, phase, name, planHash, requestDigest,
        targetProjectId, appId, error, result, System.currentTimeMillis());
    }

    public TaskState withResult(TaskResult result) {
      return new TaskState(uploadId, userId, status, phase, name, planHash, requestDigest,
        targetProjectId, appId, error, result, System.currentTimeMillis());
    }
  }
}
