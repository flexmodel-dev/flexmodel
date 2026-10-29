package dev.flexmodel.apps.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.List;
import java.util.Map;

/** Shared Excel transfer protocol. Column indexes are zero based; Excel row numbers are one based. */
public final class AppTransfer {
  private AppTransfer() {}
  public static final int MAX_ROWS = 5000;
  public static final int MAX_FORMS = 20;
  public static final int MAX_COLUMNS = 200;
  public static final long MAX_BYTES = 10 * 1024 * 1024;

  @RegisterForReflection
  public record ExportRequest(List<FormSelection> forms, Boolean includeSubforms, Boolean includeReferences) {
  }
  @RegisterForReflection
  public record FormSelection(String formName, String scope, List<String> fields, List<String> recordIds,
                              String search, String viewTabId, String sortField, String sortOrder) {
  }
  @RegisterForReflection
  public record ColumnMapping(int column, String field, String kind, String targetSheet) {}
  @RegisterForReflection
  public record SheetMapping(String sheet, String formName, int headerRow, Integer keyColumn,
                             List<ColumnMapping> columns) {}
  @RegisterForReflection
  public record PreviewRequest(List<SheetMapping> sheets) {}
  @RegisterForReflection
  public record CommitRequest(String planHash) {}
  @RegisterForReflection
  public record CellValue(String value, String display, String type) {}
  @RegisterForReflection
  public record SheetData(String name, List<List<CellValue>> rows) {}
  @RegisterForReflection
  public record WorkbookData(List<SheetData> sheets, List<SheetMapping> suggestions, boolean platform) {}
  @RegisterForReflection
  public record SheetInfo(String name, int rowCount, List<List<CellValue>> sample) {}
  @RegisterForReflection
  public record UploadResponse(String uploadId, long expiresAt, List<SheetInfo> sheets,
                               List<SheetMapping> suggestions, boolean platform) {}
  @RegisterForReflection
  public record Issue(String sheet, int row, String field, String value, String message) {}
  @RegisterForReflection
  public record PlannedRow(String sheet, String formName, int row, String key, String id,
                           Map<String, Object> values, List<String> dependencies) {}
  @RegisterForReflection
  public record FormSummary(String sheet, String formName, int count) {
  }
  @RegisterForReflection
  public record PreviewResponse(String importId, String planHash, long expiresAt, String status,
                                List<FormSummary> forms, List<Issue> errors, List<PlannedRow> sample) {
  }
  @RegisterForReflection
  public record TransferResult(String importId, String status, int total, List<FormSummary> forms) {
  }

  @RegisterForReflection
  public record Receipt(String appId, String userId, String planHash, TransferResult result) {
  }
  @RegisterForReflection
  public record ImportPlan(String id, String appId, String userId, long expiresAt, String planHash,
                           Map<String, String> formHashes, List<PlannedRow> rows,
                           List<FormSummary> forms, List<Issue> errors) {
  }
  @RegisterForReflection
  public record StagedUpload(String appId, String userId, long expiresAt, WorkbookData workbook) {}
  @RegisterForReflection
  public record ExportForm(AppFormResponse form, List<String> fields, List<Map<String, Object>> rows) {
  }
}
