package dev.flexmodel.apps;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.flexmodel.apps.dto.AppFormFieldResponse;
import dev.flexmodel.apps.dto.AppFormResponse;
import dev.flexmodel.apps.dto.AppTransfer.*;
import dev.flexmodel.common.ValidationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Function;

import static dev.flexmodel.apps.dto.AppTransfer.*;
import static dev.flexmodel.apps.AppTransferExcel.fail;

@ApplicationScoped
public class AppTransferPlanner {
  @Inject ObjectMapper json;
  @Inject AppRecordService records;

  ImportPlan plan(String appId, String userId, WorkbookData workbook, PreviewRequest request,
                  Function<String, AppFormResponse> forms, BiPredicate<String, String> exists) {
    if (request == null || request.sheets() == null || request.sheets().isEmpty() || request.sheets().size() > MAX_FORMS)
      fail("请选择 1 至 20 个表单进行映射");
    Map<String, SheetMapping> mappings = new LinkedHashMap<>();
    Map<String, AppFormResponse> resolved = new LinkedHashMap<>();
    Set<String> targets = new HashSet<>();
    List<Issue> errors = new LimitedIssues();
    for (SheetMapping mapping : request.sheets()) {
      if (mapping == null || mapping.sheet() == null || mapping.headerRow() < 1 || mapping.headerRow() > 100)
        fail("表头行必须为 1 至 100");
      if (mapping.formName() == null || mapping.formName().isBlank()) fail("请选择目标表单");
      if (mapping.columns() == null) fail("请选择字段映射");
      if (mappings.putIfAbsent(mapping.sheet(), mapping) != null) fail("Sheet 映射重复");
      if (!targets.add(mapping.formName())) fail("同一目标表单只能映射一次");
      AppFormResponse form = forms.apply(mapping.formName());
      if (form.builtin() || RECEIPT.equals(form.name())) fail("内置表单不允许导入");
      resolved.put(mapping.sheet(), form);
      SheetData sheet = sheet(workbook, mapping.sheet());
      List<CellValue> header = mapping.headerRow() <= sheet.rows().size() ? sheet.rows().get(mapping.headerRow() - 1) : List.of();
      Set<String> labels = new HashSet<>();
      for (CellValue cell : header) if (!cell.value().isBlank() && !labels.add(cell.value()))
        errors.add(new Issue(sheet.name(), mapping.headerRow(), "", cell.value(), "表头名称重复，请修改后重新上传"));
      Set<Integer> columns = new HashSet<>();
      if (mapping.keyColumn() != null) {
        if (mapping.keyColumn() < 0 || mapping.keyColumn() >= header.size()) fail("包内键列不存在");
        columns.add(mapping.keyColumn());
      }
      Set<String> fields = new HashSet<>();
      for (ColumnMapping column : mapping.columns() == null ? List.<ColumnMapping>of() : mapping.columns()) {
        if (column == null || column.kind() == null) fail("字段映射不能为空");
        if (column.column() < 0 || column.column() >= header.size() || !columns.add(column.column())) fail("源列不存在或重复映射");
        AppFormFieldResponse field = field(form, column.field());
        if (field.builtin() || !AppFieldRules.writable(field, true)) fail("字段不可导入: " + field.label());
        boolean reference = field.referenceForm() != null;
        if ((!reference && !"VALUE".equals(column.kind())) || (reference && !Set.of("KEY", "EXISTING").contains(column.kind())))
          fail("字段映射类型不合法: " + field.label());
        if (!fields.add(column.field() + "/" + column.kind())) fail("目标字段重复映射: " + field.label());
      }
    }
    for (SheetMapping mapping : mappings.values()) {
      for (ColumnMapping column : mapping.columns()) if ("KEY".equals(column.kind()) && column.targetSheet() != null) {
        AppFormResponse target = resolved.get(column.targetSheet());
        if (target == null || !target.name().equals(field(resolved.get(mapping.sheet()), column.field()).referenceForm()))
          fail("包内引用 Sheet 与字段的目标表单不一致");
        if (mappings.get(column.targetSheet()).keyColumn() == null) fail("被引用的 Sheet 必须配置包内键列");
      }
    }
    List<PlannedRow> rows = new ArrayList<>();
    Map<String, PlannedRow> byKey = new LinkedHashMap<>();
    Map<String, Map<String, String>> localReferences = new HashMap<>();
    List<FormSummary> summaries = new ArrayList<>();
    for (SheetMapping mapping : mappings.values()) {
      SheetData sheet = sheet(workbook, mapping.sheet());
      AppFormResponse form = resolved.get(mapping.sheet());
      int count = 0;
      for (int r = mapping.headerRow(); r < sheet.rows().size(); r++) {
        List<CellValue> cells = sheet.rows().get(r);
        if (cells.stream().allMatch(cell -> cell.value().isBlank())) continue;
        if (rows.size() >= MAX_ROWS) fail("整包最多允许 5,000 条记录");
        String key = mapping.keyColumn() == null ? "row-" + (r + 1) : cell(cells, mapping.keyColumn()).value();
        if (mapping.keyColumn() != null && Set.of("FORMULA", "ERROR").contains(cell(cells, mapping.keyColumn()).type()))
          errors.add(new Issue(sheet.name(), r + 1, "__key", key, "包内键不支持公式或错误单元格"));
        if (key.isBlank() || key.length() > 256) errors.add(new Issue(sheet.name(), r + 1, "__key", key, "包内键不能为空且不能超过256字符"));
        Map<String, Object> input = new LinkedHashMap<>();
        Map<String, String> refs = new LinkedHashMap<>();
        for (ColumnMapping column : mapping.columns()) {
          CellValue value = cell(cells, column.column());
          if (value.value().isBlank()) continue;
          try {
            if (Set.of("FORMULA", "ERROR").contains(value.type())) fail("不支持公式或错误单元格");
            AppFormFieldResponse field = field(form, column.field());
            if (input.containsKey(field.name())) fail("包内键和已有 ID 只能填写一个");
            if ("KEY".equals(column.kind())) {
              if (column.targetSheet() == null || !mappings.containsKey(column.targetSheet())) fail("请选择包内引用的目标 Sheet");
              input.put(field.name(), value.value());
              refs.put(field.name(), node(column.targetSheet(), value.value()));
            } else if ("EXISTING".equals(column.kind())) input.put(field.name(), value.value());
            else input.put(field.name(), convert(field, value, workbook.platform()));
          } catch (RuntimeException error) {
            errors.add(new Issue(sheet.name(), r + 1, column.field(), value.value(), error.getMessage()));
          }
        }
        PlannedRow row = new PlannedRow(sheet.name(), form.name(), r + 1, key, UUID.randomUUID().toString(), input, new ArrayList<>());
        String identity = node(sheet.name(), key);
        if (byKey.putIfAbsent(identity, row) != null) errors.add(new Issue(sheet.name(), r + 1, "__key", key, "包内键重复"));
      localReferences.put(row.id(), refs);
        rows.add(row); count++;
      }
      summaries.add(new FormSummary(sheet.name(), form.name(), count));
    }
    if (rows.isEmpty()) errors.add(new Issue("", 0, "", "", "文件中没有可导入的记录"));
    Map<String, Boolean> checked = new HashMap<>();
    for (PlannedRow row : rows) {
      var refs = localReferences.get(row.id());
      AppFormResponse form = resolved.get(row.sheet());
      for (AppFormFieldResponse field : form.fields()) {
        if (field.referenceForm() == null || row.values().get(field.name()) == null) continue;
        String raw = String.valueOf(row.values().get(field.name()));
        if (refs.containsKey(field.name())) {
          PlannedRow target = byKey.get(refs.get(field.name()));
          if (target == null) errors.add(new Issue(row.sheet(), row.row(), field.name(), raw, "包内引用记录不存在"));
          else {
            row.values().put(field.name(), target.id());
            row.dependencies().add(node(target.sheet(), target.key()));
          }
        }
      }
      // Validate logical text links after replacing package keys with their actual target UUIDs.
      // This also checks defaulted references, which may not have had a source column.
      try {
        var prepared = records.prepareImport(form, row.values());
        row.values().clear(); row.values().putAll(prepared);
      } catch (ValidationException error) { errors.add(new Issue(row.sheet(), row.row(), "", "", error.getMessage())); }
      for (var field : form.fields()) {
        Object value = row.values().get(field.name());
        if (field.referenceForm() == null || value == null || refs.containsKey(field.name())) continue;
        String id = String.valueOf(value);
        if (!checked.computeIfAbsent(node(field.referenceForm(), id), ignored -> exists.test(field.referenceForm(), id)))
          errors.add(new Issue(row.sheet(), row.row(), field.name(), id, "目标表单中不存在此记录 ID"));
      }
    }
    List<PlannedRow> sorted = sort(rows, errors);
    Map<String, String> hashes = new LinkedHashMap<>();
    resolved.values().forEach(form -> hashes.put(form.name(), hash(form)));
    String id = UUID.randomUUID().toString();
    ImportPlan plan = new ImportPlan(id, appId, userId, System.currentTimeMillis() + AppTransferStore.TTL, "", hashes, sorted, summaries, errors);
    return new ImportPlan(id, appId, userId, plan.expiresAt(), hash(plan), hashes, sorted, summaries, errors);
  }

  Object convert(AppFormFieldResponse field, CellValue value, boolean platform) {
    String text = value.value();
    return switch (field.type()) {
      case "NUMBER", "DECIMAL" -> new BigDecimal(text);
      case "BOOL" -> switch (text.trim().toLowerCase(Locale.ROOT)) {
        case "true", "是", "1" -> true;
        case "false", "否", "0" -> false;
        default -> throw new ValidationException("布尔值须为 true/false 或 是/否");
      };
      case "DATE" -> "DATE".equals(value.type()) ? LocalDateTime.parse(text).toLocalDate().toString() : LocalDate.parse(text).toString();
      case "TIME" -> "DATE".equals(value.type()) ? LocalDateTime.parse(text).toLocalTime().toString() : LocalTime.parse(text).toString();
      case "DATETIME" -> LocalDateTime.parse(text.replace(' ', 'T')).toString();
      case "MULTISELECT" -> Arrays.stream(text.split("[,，]"))
        .map(String::trim).filter(item -> !item.isEmpty())
        .map(item -> choice(field, item, platform)).toList();
      case "SELECT", "PICKLIST", "CLASSIFICATION" -> choice(field, text, platform);
      default -> "NUMERIC".equals(value.type()) ? value.display() : text;
    };
  }

  private String choice(AppFormFieldResponse field, String value, boolean platform) {
    if (field.options() == null) return value;
    if (platform) return value;
    var ids = field.options().stream().filter(option -> value.equals(option.id())).toList();
    if (ids.size() == 1) return ids.getFirst().id();
    var matches = field.options().stream().filter(option -> value.equals(option.text())).toList();
    if (matches.size() != 1) fail("选项不存在或标签重名，请使用唯一选项 ID");
    return matches.getFirst().id();
  }

  static List<PlannedRow> sort(List<PlannedRow> rows, List<Issue> errors) {
    Map<String, Integer> degrees = new LinkedHashMap<>();
    Map<String, List<String>> children = new HashMap<>();
    Map<String, PlannedRow> indexed = new LinkedHashMap<>();
    for (PlannedRow row : rows) {
      String key = node(row.sheet(), row.key()); indexed.put(key, row);
      Set<String> dependencies = new HashSet<>(row.dependencies()); degrees.put(key, dependencies.size());
      for (String parent : dependencies) children.computeIfAbsent(parent, ignored -> new ArrayList<>()).add(key);
    }
    Deque<String> ready = new ArrayDeque<>();
    degrees.forEach((key, degree) -> { if (degree == 0) ready.add(key); });
    List<PlannedRow> sorted = new ArrayList<>();
    while (!ready.isEmpty()) {
      String key = ready.removeFirst(); sorted.add(indexed.get(key));
      for (String child : children.getOrDefault(key, List.of())) if (degrees.compute(child, (ignored, degree) -> degree - 1) == 0) ready.add(child);
    }
    if (sorted.size() != rows.size()) {
      degrees.forEach((key, degree) -> { if (degree > 0) { var row = indexed.get(key); errors.add(new Issue(row.sheet(), row.row(), "", row.key(), "存在循环引用，首版不支持导入")); } });
      return rows;
    }
    return sorted;
  }

  String hash(Object value) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(value))); }
    catch (Exception error) { throw new IllegalStateException(error); }
  }
  static String node(String sheet, String key) { return sheet + "\u0000" + key; }
  static SheetData sheet(WorkbookData workbook, String name) {
    return workbook.sheets().stream().filter(sheet -> sheet.name().equals(name)).findFirst().orElseThrow(() -> new ValidationException("Sheet 不存在: " + name));
  }
  static AppFormFieldResponse field(AppFormResponse form, String name) {
    return form.fields().stream().filter(field -> field.name().equals(name)).findFirst().orElseThrow(() -> new ValidationException("字段不存在: " + name));
  }
  static CellValue cell(List<CellValue> cells, int index) { return index < cells.size() ? cells.get(index) : new CellValue("", "", "BLANK"); }

  private static final class LimitedIssues extends ArrayList<Issue> {
    @Override public boolean add(Issue issue) {
      if (size() < 500) return super.add(issue);
      if (size() == 500) return super.add(new Issue("", 0, "", "", "错误超过500项，请先修正已列出的错误后重新预览"));
      return false;
    }
  }
}
