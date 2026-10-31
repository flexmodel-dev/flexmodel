package dev.flexmodel.apps.record;

import dev.flexmodel.apps.form.dto.AppFormFieldResponse;
import dev.flexmodel.apps.form.dto.AppFormResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AppRecordHistoryDiffTest {

  private static AppFormFieldResponse field(String name, String label, String type) {
    return new AppFormFieldResponse(name, label, type, false, false, null, null);
  }

  private static AppFormResponse form(AppFormFieldResponse... fields) {
    return new AppFormResponse("orders", "订单", "", "name", "name", true, false,
      LocalDateTime.now(), LocalDateTime.now(), List.of(fields), null, List.of());
  }

  private static Map<String, Object> record(Object... pairs) {
    Map<String, Object> result = new HashMap<>();
    for (int index = 0; index + 1 < pairs.length; index += 2) {
      result.put(String.valueOf(pairs[index]), pairs[index + 1]);
    }
    return result;
  }

  @Test
  void identicalValuesProduceNoChange() {
    AppFormResponse form = form(field("amount", "金额", "DECIMAL"), field("name", "名称", "TEXT"));
    List<Map<String, Object>> changes = AppRecordHistoryDiff.changes(form,
      record("amount", "100.00", "name", "订单A", "updated_at", LocalDateTime.now()),
      record("amount", "100.00", "name", "订单A", "updated_at", LocalDateTime.now().plusMinutes(1)));
    assertTrue(changes.isEmpty());
  }

  @Test
  void systemOnlyChangesAreIgnored() {
    AppFormResponse form = form(field("amount", "金额", "DECIMAL"));
    List<Map<String, Object>> changes = AppRecordHistoryDiff.changes(form,
      record("id", "r1", "created_by", "u1", "updated_by", "u1", "updated_at", LocalDateTime.now()),
      record("id", "r2", "created_by", "u1", "updated_by", "u2", "updated_at", LocalDateTime.now().plusMinutes(1)));
    assertTrue(changes.isEmpty());
  }

  @Test
  void nullEmptyZeroFalseAreDistinctValues() {
    AppFormResponse form = form(field("note", "备注", "TEXT"), field("count", "数量", "NUMBER"),
      field("active", "启用", "BOOL"));
    List<Map<String, Object>> changes = AppRecordHistoryDiff.changes(form,
      record("note", null, "count", 0, "active", false),
      record("note", "", "count", null, "active", false));
    assertEquals(2, changes.size());
    assertEquals("note", changes.get(0).get("fieldName"));
    assertEquals("count", changes.get(1).get("fieldName"));
  }

  @Test
  void numericOneAndOnePointZeroAreEqual() {
    AppFormResponse form = form(field("amount", "金额", "DECIMAL"));
    assertTrue(AppRecordHistoryDiff.changes(form,
      record("amount", "1"), record("amount", "1.0")).isEmpty());
    assertEquals(1, AppRecordHistoryDiff.changes(form,
      record("amount", "1"), record("amount", "1.5")).size());
  }

  @Test
  void tagReorderIsNotChangeButMultiselectOrderIs() {
    AppFormResponse form = form(field("tags", "标签", "TAG"), field("levels", "等级", "MULTISELECT"));
    assertTrue(AppRecordHistoryDiff.changes(form,
      record("tags", List.of("a", "b")), record("tags", List.of("b", "a"))).isEmpty());
    assertEquals(1, AppRecordHistoryDiff.changes(form,
      record("levels", List.of("a", "b")), record("levels", List.of("b", "a"))).size());
  }

  @Test
  void jsonKeyOrderIsIgnored() {
    AppFormResponse form = form(field("location", "位置", "LOCATION"));
    assertTrue(AppRecordHistoryDiff.changes(form,
      record("location", "{\"city\":\"北京\",\"lat\":1}"),
      record("location", "{\"lat\":1,\"city\":\"北京\"}")).isEmpty());
  }

  @Test
  void displayUsesBoolTextAndAttachmentNames() {
    AppFormResponse form = form(field("active", "启用", "BOOL"),
      field("files", "附件", "FILE"));
    List<Map<String, Object>> changes = AppRecordHistoryDiff.changes(form,
      record("active", false, "files", null),
      record("active", true, "files", "[{\"name\":\"合同.pdf\"},{\"name\":\"发票.png\"}]"));
    assertEquals(2, changes.size());
    assertEquals("否", changes.get(0).get("beforeDisplay"));
    assertEquals("是", changes.get(0).get("afterDisplay"));
    assertEquals("合同.pdf、发票.png", changes.get(1).get("afterDisplay"));
    assertNull(changes.get(1).get("beforeDisplay"));
  }

  @Test
  void initialValuesSkipSystemFieldsAndMissingValues() {
    AppFormResponse form = form(field("name", "名称", "TEXT"), field("amount", "金额", "DECIMAL"));
    List<Map<String, Object>> initial = AppRecordHistoryDiff.initialValues(form,
      record("id", "r1", "name", "订单A", "amount", null, "created_at", LocalDateTime.now()));
    assertEquals(1, initial.size());
    assertEquals("name", initial.getFirst().get("fieldName"));
    assertEquals("订单A", initial.getFirst().get("after"));
    assertNull(initial.getFirst().get("before"));
  }
}
