package dev.flexmodel.apps;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.flexmodel.apps.dto.AppFormFieldResponse;
import dev.flexmodel.apps.dto.AppTransfer.*;
import dev.flexmodel.apps.dto.AppTransfer.CellValue;
import dev.flexmodel.common.ValidationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.ss.util.NumberToTextConverter;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipFile;

import static dev.flexmodel.apps.dto.AppTransfer.*;

@ApplicationScoped
public class AppTransferExcel {
  static final String META = "__flexmodel";
  static final String HELP = "__说明";
  @Inject ObjectMapper json;

  public WorkbookData read(Path file) {
    // Bound decompressed input before POI materializes the OOXML document in memory.
    try (ZipFile zip = new ZipFile(file.toFile())) {
      long bytes = 0;
      int entries = 0;
      var iterator = zip.entries();
      byte[] buffer = new byte[8192];
      while (iterator.hasMoreElements()) {
        var entry = iterator.nextElement();
        if (++entries > 1000) fail("Excel 压缩包条目过多");
        try (InputStream input = zip.getInputStream(entry)) {
          int count;
          while ((count = input.read(buffer)) != -1) {
            bytes += count;
            if (bytes > 50 * 1024 * 1024) fail("Excel 解压后超过 50 MB");
          }
        }
      }
    } catch (IOException error) { throw new ValidationException("请上传有效的 .xlsx 文件", error); }
    try (var input = Files.newInputStream(file); var workbook = new XSSFWorkbook(input)) {
      if (workbook.getNumberOfSheets() > MAX_FORMS + 2) fail("最多允许 20 个业务表单");
      List<SheetMapping> suggestions = new ArrayList<>();
      Sheet meta = workbook.getSheet(META);
      if (meta != null) {
        if (!"flexmodel-excel-v1".equals(text(meta.getRow(0), 0))) fail("不支持的数据包版本");
        for (int i = 1; i <= meta.getLastRowNum(); i++) {
          String value = text(meta.getRow(i), 0);
          if (!value.isBlank()) suggestions.add(json.readValue(value, SheetMapping.class));
        }
      }
      List<SheetData> sheets = new ArrayList<>();
      int total = 0;
      DataFormatter formatter = new DataFormatter(Locale.ROOT);
      for (Sheet sheet : workbook) {
        if (META.equals(sheet.getSheetName()) || HELP.equals(sheet.getSheetName())) continue;
        if (sheet.getLastRowNum() > MAX_ROWS + 100) fail("Excel 行数超过上限");
        List<List<CellValue>> rows = new ArrayList<>();
        for (int r = 0; r <= sheet.getLastRowNum(); r++) {
          Row row = sheet.getRow(r);
          int width = row == null ? 0 : Math.max(0, row.getLastCellNum());
          if (width > MAX_COLUMNS) fail("每个表单最多允许 200 列");
          List<CellValue> values = new ArrayList<>();
          for (int c = 0; c < width; c++) values.add(readCell(row.getCell(c), formatter));
          rows.add(values);
          if (values.stream().anyMatch(value -> !value.value().isBlank()) && ++total > MAX_ROWS + MAX_FORMS * 100)
            fail("Excel 数据量超过上限");
        }
        sheets.add(new SheetData(sheet.getSheetName(), rows));
      }
      if (sheets.isEmpty() || sheets.size() > MAX_FORMS) fail("请选择 1 至 20 个业务表单");
      return new WorkbookData(sheets, suggestions, meta != null);
    } catch (ValidationException error) { throw error; }
    catch (Exception error) { throw new ValidationException("Excel 解析失败，请检查文件格式", error); }
  }

  private CellValue readCell(Cell cell, DataFormatter formatter) {
    if (cell == null) return new CellValue("", "", "BLANK");
    return switch (cell.getCellType()) {
      case STRING -> new CellValue(cell.getStringCellValue(), cell.getStringCellValue(), "STRING");
      case BOOLEAN -> new CellValue(String.valueOf(cell.getBooleanCellValue()), String.valueOf(cell.getBooleanCellValue()), "BOOL");
      case NUMERIC -> {
        String raw = DateUtil.isCellDateFormatted(cell)
          ? cell.getLocalDateTimeCellValue().toString() : NumberToTextConverter.toText(cell.getNumericCellValue());
        yield new CellValue(raw, formatter.formatCellValue(cell), DateUtil.isCellDateFormatted(cell) ? "DATE" : "NUMERIC");
      }
      case FORMULA -> new CellValue(cell.getCellFormula(), "公式", "FORMULA");
      case ERROR -> new CellValue("错误单元格", "错误单元格", "ERROR");
      default -> new CellValue("", "", "BLANK");
    };
  }

  public byte[] write(List<ExportForm> forms) {
    try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
      List<SheetMapping> mappings = new ArrayList<>();
      Map<String, String> sheetNames = new LinkedHashMap<>();
      Map<String, Set<String>> included = new HashMap<>();
      for (int i = 0; i < forms.size(); i++) {
        ExportForm exported = forms.get(i);
        sheetNames.put(exported.form().name(), WorkbookUtil.createSafeSheetName("T" + (i + 1) + "_" + exported.form().label()));
        Set<String> keys = new HashSet<>();
        exported.rows().forEach(row -> keys.add(String.valueOf(row.get("id"))));
        included.put(exported.form().name(), keys);
      }
      CellStyle headerStyle = workbook.createCellStyle();
      Font font = workbook.createFont(); font.setBold(true); headerStyle.setFont(font);
      for (ExportForm exported : forms) {
        Sheet sheet = workbook.createSheet(sheetNames.get(exported.form().name()));
        sheet.createFreezePane(0, 1);
        Row header = sheet.createRow(0); header.createCell(0).setCellValue("__key");
        List<ColumnMapping> columns = new ArrayList<>();
        int index = 1;
        for (AppFormFieldResponse field : exported.form().fields()) {
          if (!exported.fields().contains(field.name())) continue;
          if (field.referenceForm() != null) {
            String targetSheet = sheetNames.get(field.referenceForm());
            header.createCell(index).setCellValue(field.label() + " [" + field.name() + "]（包内键）");
            columns.add(new ColumnMapping(index++, field.name(), "KEY", targetSheet));
            header.createCell(index).setCellValue(field.label() + " [" + field.name() + "]（已有ID）");
            columns.add(new ColumnMapping(index++, field.name(), "EXISTING", null));
          } else {
            header.createCell(index).setCellValue(field.label() + " [" + field.name() + "]");
            columns.add(new ColumnMapping(index++, field.name(), "VALUE", null));
          }
        }
        if (index > MAX_COLUMNS) fail("导出字段超过 200 列");
        for (Cell cell : header) cell.setCellStyle(headerStyle);
        for (int r = 0; r < exported.rows().size(); r++) {
          Map<String, Object> record = exported.rows().get(r);
          Row row = sheet.createRow(r + 1);
          row.createCell(0).setCellValue(String.valueOf(record.get("id")));
          for (ColumnMapping column : columns) {
            Object value = record.get(column.field());
            if (value == null) continue;
            if (!"VALUE".equals(column.kind())) {
              AppFormFieldResponse field = exported.form().fields().stream().filter(f -> f.name().equals(column.field())).findFirst().orElseThrow();
              boolean local = included.getOrDefault(field.referenceForm(), Set.of()).contains(String.valueOf(value));
              if (local != "KEY".equals(column.kind())) continue;
            }
            // String cells preserve long numbers, leading zeroes, and never execute input as a formula.
            row.createCell(column.column()).setCellValue(value instanceof Collection<?> items
              ? String.join(", ", items.stream().map(String::valueOf).toList())
              : value instanceof Map<?, ?> ? json.writeValueAsString(value) : value.toString());
          }
        }
        for (int c = 0; c < index; c++) sheet.setColumnWidth(c, Math.min(c == 0 ? 40 : 24, 80) * 256);
        mappings.add(new SheetMapping(sheet.getSheetName(), exported.form().name(), 1, 0, columns));
      }
      Sheet help = workbook.createSheet(HELP);
      String[] instructions = {"每个表单一张 Sheet；第一行为字段名称。", "__key 是包内唯一键，导入后会生成新的记录 ID。",
        "引用字段的包内键与已有 ID 每行只能填写一个；包内键必须对应目标 Sheet 的 __key。",
        "空白表示空值；日期使用 yyyy-MM-dd，时间使用 HH:mm:ss，日期时间使用 yyyy-MM-ddTHH:mm:ss。",
        "多选使用逗号分隔，例如 选项A,选项B（中英文逗号均可）；布尔值使用 true/false 或 是/否。", "不支持公式；所有者、创建人和时间由服务端生成。"};
      for (int i = 0; i < instructions.length; i++) help.createRow(i).createCell(0).setCellValue(instructions[i]);
      help.setColumnWidth(0, 100 * 256);
      Sheet meta = workbook.createSheet(META);
      meta.createRow(0).createCell(0).setCellValue("flexmodel-excel-v1");
      for (int i = 0; i < mappings.size(); i++) meta.createRow(i + 1).createCell(0).setCellValue(json.writeValueAsString(mappings.get(i)));
      workbook.setSheetHidden(workbook.getSheetIndex(meta), true);
      workbook.write(output);
      if (output.size() > MAX_BYTES) fail("导出文件超过 10 MB，请缩小导出范围");
      return output.toByteArray();
    } catch (ValidationException error) { throw error; }
    catch (IOException error) { throw new IllegalStateException("生成 Excel 失败", error); }
  }

  private String text(Row row, int column) {
    return row == null || row.getCell(column) == null ? "" : row.getCell(column).getStringCellValue();
  }
  static void fail(String message) { throw new ValidationException(message); }
}
