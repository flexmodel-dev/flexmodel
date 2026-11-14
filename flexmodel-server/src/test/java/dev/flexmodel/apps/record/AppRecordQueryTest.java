package dev.flexmodel.apps.record;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.apps.form.AppFormService;
import dev.flexmodel.apps.form.dto.AppFormFieldResponse;
import dev.flexmodel.apps.form.dto.AppFormResponse;
import dev.flexmodel.apps.form.dto.AppFormViewTabRecord;
import dev.flexmodel.common.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AppRecordQueryTest {
  private final AppFormService forms = new AppFormService();
  private final AppFormResponse form = new AppFormResponse(
    "orders", "订单", "", "title", "title", true, false, LocalDateTime.now(), LocalDateTime.now(),
    List.of(new AppFormFieldResponse("title", "名称", "TEXT", false, false, null, null),
      new AppFormFieldResponse("amount", "金额", "DECIMAL", false, false, null, null),
      new AppFormFieldResponse("approved", "已确认", "BOOL", false, false, null, null),
      new AppFormFieldResponse("created_by", "创建人", "TEXT", false, true, null, null)), null,
    List.of(new AppFormViewTabRecord("active", "有效", Map.of("_and", List.of(Map.of("approved", Map.of("_eq", false)))))));

  @Test
  void combinesAdvancedOrWithTabAndKeywordUsingAnd() {
    AppRecordService records = new AppRecordService();
    records.appFormService = forms;
    String query = "{\"_or\":[{\"amount\":{\"_gte\":0}},{\"approved\":{\"_eq\":false}}]}";
    Map<String, Object> combined = JsonUtils.parseToMap(records.buildFilter(form, "采购", "active", null, query));
    List<?> groups = (List<?>) combined.get("_and");
    assertEquals(3, groups.size());
    assertEquals(JsonUtils.parseToMap(query), groups.getFirst());
  }

  @Test
  void clearingQueryRestoresUnfilteredRequest() {
    AppRecordService records = new AppRecordService();
    records.appFormService = forms;
    assertNull(records.buildFilter(form, null, null, null, null));
  }

  @ParameterizedTest
  @ValueSource(strings = {"invalid", "[]", "null", "{}", "{\"_and\":null}", "{\"_or\":[]}",
    "{\"_and\":[{\"missing\":{\"_eq\":1}}]}", "{\"_and\":[{\"created_by\":{\"_eq\":\"admin\"}}]}",
    "{\"_and\":[{\"amount\":{\"_contains\":\"1\"}}]}", "{\"_and\":[{\"amount\":{\"_eq\":\"1\"}}]}",
    "{\"_and\":[{\"approved\":{\"_eq\":\"false\"}}]}", "{\"_and\":[{\"title\":{\"_eq\":\"\"}}]}",
    "{\"_and\":[{\"_or\":[{\"title\":{\"_eq\":\"x\"}}]}]}"})
  void rejectsMalformedOrUnsupportedQuery(String query) {
    assertThrows(ValidationException.class, () -> forms.normalizeQueryFilter(form, query));
  }

  @Test
  void limitsConditionCountAndPayload() {
    var conditions = java.util.Collections.nCopies(11, Map.of("title", Map.of("_eq", "x")));
    assertThrows(ValidationException.class, () -> forms.normalizeQueryFilter(form, JsonUtils.toJsonString(Map.of("_and", conditions))));
    assertThrows(ValidationException.class, () -> forms.normalizeQueryFilter(form, "x".repeat(16001)));
  }
}
