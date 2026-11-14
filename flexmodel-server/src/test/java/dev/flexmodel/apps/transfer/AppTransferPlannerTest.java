package dev.flexmodel.apps.transfer;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.flexmodel.apps.form.dto.*;
import dev.flexmodel.apps.record.AppRecordService;
import dev.flexmodel.apps.transfer.dto.AppTransfer.*;
import dev.flexmodel.common.ValidationException;
import jakarta.ws.rs.WebApplicationException;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class AppTransferPlannerTest {
  @TempDir Path directory;
  ObjectMapper json = new ObjectMapper().findAndRegisterModules();
  AppTransferPlanner planner() {
    var planner = new AppTransferPlanner(); planner.json = json; planner.records = new AppRecordService(); return planner;
  }
  AppTransferExcel excel() { var codec = new AppTransferExcel(); codec.json = json; return codec; }
  AppFormFieldResponse field(String name, String type, String target) {
    return new AppFormFieldResponse(name, name, type, false, false, null, target);
  }
  AppFormResponse form(String name, AppFormFieldResponse... fields) {
    return new AppFormResponse(name, name, "", "name", "name", true, false, LocalDateTime.of(2026, 1, 1, 0, 0), null,
      List.of(fields), new AppFormLayoutRecord(List.of()), List.of());
  }
  SheetData sheet(String name, String[]... rows) {
    return new SheetData(name, Arrays.stream(rows).map(row -> Arrays.stream(row).map(value -> new CellValue(value, value, "STRING")).toList()).toList());
  }
  SheetMapping mapping(String sheet, String target, ColumnMapping... columns) { return new SheetMapping(sheet, target, 1, 0, List.of(columns)); }
  ColumnMapping column(int index, String field) { return new ColumnMapping(index, field, "VALUE", null); }

  @Test
  void remapsThreeFormsAndSortsActualDependencies() {
    var supplier = form("supplier", field("name", "TEXT", null));
    var order = form("orders", field("name", "TEXT", null), field("supplier", "REFERENCE", "supplier"));
    var item = form("items", field("order", "REFERENCE", "orders"));
    Map<String, AppFormResponse> forms = Map.of("supplier", supplier, "orders", order, "items", item);
    var workbook = new WorkbookData(List.of(
      sheet("Items", new String[]{"key", "order"}, new String[]{"L1", "O1"}, new String[]{"L2", "O1"}),
      sheet("Orders", new String[]{"key", "name", "supplier"}, new String[]{"O1", "订单", "S1"}),
      sheet("Suppliers", new String[]{"key", "name"}, new String[]{"S1", "甲公司"})), List.of(), false);
    var mappings = List.of(mapping("Items", "items", new ColumnMapping(1, "order", "KEY", "Orders")),
      mapping("Orders", "orders", column(1, "name"), new ColumnMapping(2, "supplier", "KEY", "Suppliers")),
      mapping("Suppliers", "supplier", column(1, "name")));
    var plan = planner().plan("app", "person", workbook, new PreviewRequest(mappings), forms::get, (name, id) -> false);
    assertTrue(plan.errors().isEmpty(), plan.errors().toString());
    assertEquals(List.of("supplier", "orders", "items", "items"), plan.rows().stream().map(PlannedRow::formName).toList());
    assertEquals(plan.rows().get(0).id(), plan.rows().get(1).values().get("supplier"));
    assertEquals(plan.rows().get(1).id(), plan.rows().get(2).values().get("order"));
    assertNotEquals("O1", plan.rows().get(1).id());
  }

  @Test void allowsAcyclicSelfReferenceAndReportsCycles() {
    var form = form("departments", field("parent", "REFERENCE", "departments"));
    var mapping = mapping("部门", "departments", new ColumnMapping(1, "parent", "KEY", "部门"));
    var acyclic = new WorkbookData(List.of(sheet("部门", new String[]{"key", "parent"}, new String[]{"child", "root"}, new String[]{"root", ""})), List.of(), false);
    var result = planner().plan("app", "person", acyclic, new PreviewRequest(List.of(mapping)), ignored -> form, (n, id) -> false);
    assertTrue(result.errors().isEmpty()); assertEquals("root", result.rows().getFirst().key());
    var cyclic = new WorkbookData(List.of(sheet("部门", new String[]{"key", "parent"}, new String[]{"A", "B"}, new String[]{"B", "A"})), List.of(), false);
    assertTrue(planner().plan("app", "person", cyclic, new PreviewRequest(List.of(mapping)), ignored -> form, (n, id) -> false)
      .errors().stream().anyMatch(error -> error.message().contains("循环引用")));
  }

  @Test void validatesTextLinkRulesAfterRemappingPackageKeys() {
    var config = new AppFieldConfig(); config.setPattern("[0-9a-f-]{36}");
    var parent = new AppFormFieldResponse("parent", "parent", "TEXT", false, false, null, "nodes", false, false, config);
    var form = form("nodes", parent);
    var workbook = new WorkbookData(List.of(sheet("树", new String[]{"key", "parent"}, new String[]{"ROOT", ""}, new String[]{"CHILD", "ROOT"})), List.of(), false);
    var plan = planner().plan("a", "u", workbook, new PreviewRequest(List.of(mapping("树", "nodes", new ColumnMapping(1, "parent", "KEY", "树")))), n -> form, (n, id) -> false);
    assertTrue(plan.errors().isEmpty(), plan.errors().toString());
    assertEquals(plan.rows().getFirst().id(), plan.rows().get(1).values().get("parent"));
  }

  @Test void reportsMissingDuplicateAndAmbiguousReferences() {
    var form = form("orders", field("ref", "REFERENCE", "orders"));
    var workbook = new WorkbookData(List.of(sheet("订单", new String[]{"key", "ref", "existing"},
      new String[]{"A", "missing", ""}, new String[]{"A", "A", "old"})), List.of(), false);
    var mapping = mapping("订单", "orders", new ColumnMapping(1, "ref", "KEY", "订单"), new ColumnMapping(2, "ref", "EXISTING", null));
    var errors = planner().plan("a", "u", workbook, new PreviewRequest(List.of(mapping)), n -> form, (n, id) -> true).errors();
    assertTrue(errors.stream().anyMatch(error -> error.message().contains("重复")));
    assertTrue(errors.stream().anyMatch(error -> error.message().contains("只能填写一个")));
    assertTrue(errors.stream().anyMatch(error -> error.message().contains("不存在")));
  }

  @Test void checksExternalIdsAndRejectsProtectedFields() {
    var builtin = new AppFormFieldResponse("id", "id", "TEXT", false, true, null, null);
    var form = form("orders", field("ref", "REFERENCE", "supplier"), builtin);
    var workbook = new WorkbookData(List.of(sheet("订单", new String[]{"key", "ref"}, new String[]{"A", "existing"})), List.of(), false);
    var mapping = mapping("订单", "orders", new ColumnMapping(1, "ref", "EXISTING", null));
    assertTrue(planner().plan("a", "u", workbook, new PreviewRequest(List.of(mapping)), n -> form, (n, id) -> id.equals("existing")).errors().isEmpty());
    assertFalse(planner().plan("a", "u", workbook, new PreviewRequest(List.of(mapping)), n -> form, (n, id) -> false).errors().isEmpty());
    assertThrows(ValidationException.class, () -> planner().plan("a", "u", workbook,
      new PreviewRequest(List.of(mapping("订单", "orders", column(1, "id")))), n -> form, (n, id) -> true));
  }

  @Test void preservesTextAndConvertsNumbersBooleansDatesAndChoices() {
    var planner = planner();
    assertEquals("00123", planner.convert(field("code", "TEXT", null), new CellValue("123", "00123", "NUMERIC"), false));
    assertEquals("9007199254740993", planner.convert(field("code", "TEXT", null), new CellValue("9007199254740993", "9007199254740993", "STRING"), false));
    assertEquals(false, planner.convert(field("flag", "BOOL", null), new CellValue("false", "false", "BOOL"), false));
    assertEquals(new java.math.BigDecimal("0"), planner.convert(field("amount", "DECIMAL", null), new CellValue("0", "0", "NUMERIC"), false));
    assertEquals("2026-10-29", planner.convert(field("date", "DATE", null), new CellValue("2026-10-29T00:00", "", "DATE"), false));
    var choices = new AppFormFieldResponse("choice", "choice", "SELECT", false, false,
      List.of(new AppFormFieldOptionResponse("x", "中文", null, false)), null);
    assertEquals("x", planner.convert(choices, new CellValue("中文", "中文", "STRING"), false));
    var multi = new AppFormFieldResponse("tags", "tags", "MULTISELECT", false, false,
      List.of(new AppFormFieldOptionResponse("a", "甲", null, false), new AppFormFieldOptionResponse("b", "乙", null, false)), null);
    assertEquals(List.of("a", "b"), planner.convert(multi, new CellValue("甲, 乙", "甲, 乙", "STRING"), false));
    assertEquals(List.of("a", "b"), planner.convert(multi, new CellValue("甲，乙", "甲，乙", "STRING"), false));
    assertEquals(List.of("a"), planner.convert(multi, new CellValue(" a ,,", "a ,,", "STRING"), true));
  }

  @Test void roundTripsWorkbookMetadataAndLiteralCells() throws Exception {
    var form = form("orders", field("name", "TEXT", null));
    byte[] bytes = excel().write(List.of(new ExportForm(form, List.of("name"), List.of(Map.of("id", "old", "name", "=SUM(1,2)\n中文,数据")))));
    Path file = directory.resolve("data.xlsx"); Files.write(file, bytes);
    var workbook = excel().read(file);
    assertTrue(workbook.platform()); assertEquals(1, workbook.suggestions().size());
    assertEquals("=SUM(1,2)\n中文,数据", workbook.sheets().getFirst().rows().get(1).get(1).value());
    assertEquals("STRING", workbook.sheets().getFirst().rows().get(1).get(1).type());
    var result = planner().plan("app", "u", workbook, new PreviewRequest(workbook.suggestions()), n -> form, (n, id) -> false);
    assertTrue(result.errors().isEmpty());
  }

  @Test
  void writesMultiselectValuesAsCommaSeparatedText() throws Exception {
    var form = form("orders", new AppFormFieldResponse("tags", "tags", "MULTISELECT", false, false,
      List.of(new AppFormFieldOptionResponse("a", "甲", null, false)), null));
    byte[] bytes = excel().write(List.of(new ExportForm(form, List.of("tags"), List.of(Map.of("id", "1", "tags", List.of("a", "b"))))));
    Path file = directory.resolve("multi.xlsx");
    Files.write(file, bytes);
    assertEquals("a, b", excel().read(file).sheets().getFirst().rows().get(1).get(1).value());
  }

  @Test void reportsFormulaErrorAndDuplicateHeaders() throws Exception {
    var form = form("orders", field("name", "TEXT", null));
    try (var workbook = new XSSFWorkbook()) {
      var sheet = workbook.createSheet("数据"); var header = sheet.createRow(0);
      header.createCell(0).setCellValue("重复"); header.createCell(1).setCellValue("重复");
      sheet.createRow(1).createCell(1).setCellFormula("1+1");
      Path file = directory.resolve("formula.xlsx"); try (var out = Files.newOutputStream(file)) { workbook.write(out); }
      var parsed = excel().read(file);
      var result = planner().plan("app", "u", parsed, new PreviewRequest(List.of(mapping("数据", "orders", column(1, "name")))), n -> form, (n, id) -> true);
      assertTrue(result.errors().stream().anyMatch(error -> error.message().contains("公式")));
      assertTrue(result.errors().stream().anyMatch(error -> error.message().contains("表头名称重复")));
    }
  }

  @Test void rejectsRowLimitAndIncorrectReferenceTargets() {
    var form = form("orders", field("name", "TEXT", null));
    List<List<CellValue>> rows = new ArrayList<>(); rows.add(List.of(new CellValue("name", "name", "STRING")));
    for (int i = 0; i < 5001; i++) rows.add(List.of(new CellValue("row" + i, "row" + i, "STRING")));
    var workbook = new WorkbookData(List.of(new SheetData("数据", rows)), List.of(), false);
    assertThrows(ValidationException.class, () -> planner().plan("a", "u", workbook,
      new PreviewRequest(List.of(new SheetMapping("数据", "orders", 1, null, List.of(column(0, "name"))))), n -> form, (n, id) -> true));
    var referenceForm = form("orders", field("ref", "REFERENCE", "supplier"));
    var tiny = new WorkbookData(List.of(sheet("订单", new String[]{"key", "ref"}, new String[]{"A", "A"})), List.of(), false);
    assertThrows(ValidationException.class, () -> planner().plan("a", "u", tiny,
      new PreviewRequest(List.of(mapping("订单", "orders", new ColumnMapping(1, "ref", "KEY", "订单")))), n -> referenceForm, (n, id) -> true));
  }

  @Test void rejectsOversizedSheetsAndExpiredOrForeignStages() throws Exception {
    try (var workbook = new XSSFWorkbook()) {
      workbook.createSheet("数据").createRow(0).createCell(200).setCellValue("超限");
      Path file = directory.resolve("wide.xlsx"); try (var out = Files.newOutputStream(file)) { workbook.write(out); }
      assertThrows(ValidationException.class, () -> excel().read(file));
    }
    var store = new AppTransferStore(); store.directory = Optional.of(directory.toString()); store.json = json;
    assertEquals(404, assertThrows(WebApplicationException.class, () -> store.requireOwner("app", "other", "app", "u", Long.MAX_VALUE)).getResponse().getStatus());
    assertEquals(410, assertThrows(WebApplicationException.class, () -> store.requireOwner("app", "u", "app", "u", 0)).getResponse().getStatus());
    assertThrows(WebApplicationException.class, () -> store.file("../secret", ".plan.json"));
  }
}
