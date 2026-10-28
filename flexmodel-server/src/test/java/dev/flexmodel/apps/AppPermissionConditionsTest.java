package dev.flexmodel.apps;

import dev.flexmodel.apps.dto.AppFormFieldResponse;
import dev.flexmodel.common.ValidationException;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class AppPermissionConditionsTest {
  private static final List<AppFormFieldResponse> FIELDS = List.of(
    new AppFormFieldResponse("name", "名称", "TEXT", false, false, null, null),
    new AppFormFieldResponse("amount", "金额", "DECIMAL", false, false, null, null));

  @Test
  void acceptsSingleConditionAndNestedGroups() {
    assertDoesNotThrow(() -> AppPermissionConditions.validate(null, FIELDS));
    assertDoesNotThrow(() -> AppPermissionConditions.validate(Map.of("logic", "AND", "children", List.of(
      Map.of("field", "name", "operator", "EQ", "value", "订单"), Map.of("field", "amount", "operator", "GT", "value", 10))), FIELDS));
    assertDoesNotThrow(() -> AppPermissionConditions.validate(Map.of("field", "amount", "operator", "IS_NULL"), FIELDS));
  }

  @Test
  void rejectsUnknownFieldsScriptsAndInvalidTypes() {
    for (var condition : List.<Map<String, Object>>of(Map.of("sql", "1=1"), Map.of("field", "missing", "operator", "EQ", "value", "x"),
      Map.of("field", "amount", "operator", "EQ", "value", "10"), Map.of("field", "name", "operator", "GT", "value", "x")))
      assertThrows(ValidationException.class, () -> AppPermissionConditions.validate(condition, FIELDS));
  }

  @Test
  void rejectsEmptyOrUnboundedTreesAndCollections() {
    assertThrows(ValidationException.class, () -> AppPermissionConditions.validate(Map.of("logic", "OR", "children", List.of()), FIELDS));
    assertThrows(ValidationException.class, () -> AppPermissionConditions.validate(Map.of("field", "name", "operator", "IN", "value", List.of()), FIELDS));
    Map<String, Object> condition = Map.of("field", "name", "operator", "EQ", "value", "x");
    for (int depth = 0; depth < 10; depth++) condition = Map.of("logic", "AND", "children", List.of(condition));
    var tooDeep = condition;
    assertThrows(ValidationException.class, () -> AppPermissionConditions.validate(tooDeep, FIELDS));
  }
}
