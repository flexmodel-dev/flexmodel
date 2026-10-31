package dev.flexmodel.apps.dto;

import java.util.List;
import java.util.Map;

/** Shared Excel transfer protocol. Column indexes are zero based; Excel row numbers are one based. */
public final class AppTransfer {
  private AppTransfer() {}
  public static final int MAX_ROWS = 5000;
  public static final int MAX_FORMS = 20;
  public static final int MAX_COLUMNS = 200;
  public static final long MAX_BYTES = 10 * 1024 * 1024;

  public record ExportRequest(List<FormSelection> forms, Boolean includeSubforms, Boolean includeReferences) {
  }
  public record FormSelection(String formName, String scope, List<String> fields, List<String> recordIds,
                              String search, String viewTabId, String sortField, String sortOrder, String filter) {
  }
  public record ColumnMapping(int column, String field, String kind, String targetSheet) {}
  public record SheetMapping(String sheet, String formName, int headerRow, Integer keyColumn,
                             List<ColumnMapping> columns) {}
  public record PreviewRequest(List<SheetMapping> sheets) {}
  public record CommitRequest(String planHash) {}
  public record CellValue(String value, String display, String type) {}
  public record SheetData(String name, List<List<CellValue>> rows) {}
  public record WorkbookData(List<SheetData> sheets, List<SheetMapping> suggestions, boolean platform) {}
  public record SheetInfo(String name, int rowCount, List<List<CellValue>> sample) {}
  public record UploadResponse(String uploadId, long expiresAt, List<SheetInfo> sheets,
                               List<SheetMapping> suggestions, boolean platform) {}
  public record Issue(String sheet, int row, String field, String value, String message) {}
  public record PlannedRow(String sheet, String formName, int row, String key, String id,
                           Map<String, Object> values, List<String> dependencies) {}
  public record FormSummary(String sheet, String formName, int count) {
  }
  public record PreviewResponse(String importId, String planHash, long expiresAt, String status,
                                List<FormSummary> forms, List<Issue> errors, List<PlannedRow> sample) {
  }
  public record TransferResult(String importId, String status, int total, List<FormSummary> forms) {
  }

  public record Receipt(String appId, String userId, String planHash, TransferResult result) {
  }
  public record ImportPlan(String id, String appId, String userId, long expiresAt, String planHash,
                           Map<String, String> formHashes, List<PlannedRow> rows,
                           List<FormSummary> forms, List<Issue> errors) {
  }
  public record StagedUpload(String appId, String userId, long expiresAt, WorkbookData workbook) {}
  public record ExportForm(AppFormResponse form, List<String> fields, List<Map<String, Object>> rows) {
  }
}
