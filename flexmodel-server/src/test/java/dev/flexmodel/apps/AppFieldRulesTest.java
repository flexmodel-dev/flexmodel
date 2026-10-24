package dev.flexmodel.apps;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.apps.dto.AppFieldConfig;
import dev.flexmodel.apps.dto.AppFormFieldResponse;
import dev.flexmodel.apps.dto.AppFormFieldOptionResponse;
import dev.flexmodel.common.ValidationException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AppFieldRulesTest {
  private AppFieldConfig config(Map<String, Object> values) {
    return JsonUtils.convertValue(values, AppFieldConfig.class);
  }

  private AppFormFieldResponse field(String type, AppFieldConfig config) {
    return new AppFormFieldResponse("value", "测试字段", type, false, false, null, null, false, false, config);
  }

  @Test
  void validatesTextPatternsAndDefaultsBeforeSavingMetadata() {
    AppFieldConfig config = config(Map.of("pattern", "[A-Z]{2}[0-9]{3}", "maxLength", 5,
      "defaultValue", "AB123", "commonValues", List.of("CD456"), "validationMessage", "编号不正确"));
    AppFieldRules.validateConfig("TEXT", config);
    assertEquals("AB123", AppFieldRules.defaultValue(field("TEXT", config)));
    assertEquals("编号不正确", assertThrows(ValidationException.class,
      () -> AppFieldRules.validateValue(field("TEXT", config), "bad")).getMessage());
    assertThrows(ValidationException.class,
      () -> AppFieldRules.validateConfig("TEXT", config(Map.of("pattern", "["))));
    assertThrows(ValidationException.class,
      () -> AppFieldRules.validateConfig("TEXT", config(Map.of("maxLength", 256))));
    assertThrows(ValidationException.class,
      () -> AppFieldRules.validateConfig("TEXT", config(Map.of("defaultValue", "bad", "pattern", "[A-Z]+"))));
  }

  @Test
  void rejectsWrongTypePropertiesAndInvalidNumericValues() {
    assertThrows(ValidationException.class,
      () -> AppFieldRules.validateConfig("TEXT", config(Map.of("decimalPlaces", 2))));
    assertThrows(ValidationException.class,
      () -> AppFieldRules.validateConfig("DECIMAL", config(Map.of("decimalPlaces", 7))));
    AppFormFieldResponse decimal = field("DECIMAL", config(Map.of("decimalPlaces", 2, "allowNegative", false)));
    assertThrows(ValidationException.class, () -> AppFieldRules.validateValue(decimal, -1));
    assertThrows(ValidationException.class, () -> AppFieldRules.validateValue(decimal, "NaN"));
    assertThrows(ValidationException.class, () -> AppFieldRules.validateValue(decimal, "1.234"));
    assertDoesNotThrow(() -> AppFieldRules.validateValue(decimal, "1.2300"));
    assertThrows(ValidationException.class, () -> AppFieldRules.validateValue(field("NUMBER", null), 1.5));
    assertThrows(ValidationException.class, () -> AppFieldRules.validateValue(field("NUMBER", null), "2147483648"));
  }

  @Test
  void handlesFalseZeroEmptyAndTemporalDefaults() {
    assertEquals(false, AppFieldRules.defaultValue(field("BOOL", config(Map.of("defaultValue", false)))));
    assertEquals(0, AppFieldRules.defaultValue(field("NUMBER", config(Map.of("defaultValue", 0)))));
    assertFalse(AppFieldRules.isEmpty(false));
    assertFalse(AppFieldRules.isEmpty(0));
    assertTrue(AppFieldRules.isEmpty(List.of()));
    assertThrows(ValidationException.class,
      () -> AppFieldRules.validateConfig("DATE", config(Map.of("defaultValue", "2026-02-30"))));
    assertThrows(ValidationException.class,
      () -> AppFieldRules.validateConfig("DATE", config(Map.of("defaultNow", true, "defaultValue", "2026-10-24"))));
    assertNotNull(AppFieldRules.defaultValue(field("DATE", config(Map.of("defaultNow", true)))));
    assertThrows(ValidationException.class,
      () -> AppFieldRules.validateConfig("DATE", config(Map.of("dateFormat", "invalid"))));
  }

  @Test
  void enforcesWriteRestrictionsAndMultipleSelection() {
    AppFormFieldResponse readonly = field("TEXT", config(Map.of("creatable", false, "updatable", false)));
    assertFalse(AppFieldRules.writable(readonly, true));
    assertFalse(AppFieldRules.writable(readonly, false));
    List<AppFormFieldOptionResponse> options = List.of(
      new AppFormFieldOptionResponse("a", "甲", null, true), new AppFormFieldOptionResponse("b", "乙", null, true));
    AppFormFieldResponse multi = new AppFormFieldResponse("value", "多选", "MULTISELECT", false, false,
      options, null, false, false, config(Map.of("displayStyle", "DROPDOWN")));
    assertEquals(List.of("a", "b"), AppFieldRules.defaultValue(multi));
    assertEquals(List.of("a"), AppFieldRules.validateValue(multi, List.of("a", "a")));
    assertThrows(ValidationException.class, () -> AppFieldRules.validateValue(multi, "a"));
    assertThrows(ValidationException.class, () -> AppFieldRules.validateValue(multi, List.of("missing")));
  }
}
