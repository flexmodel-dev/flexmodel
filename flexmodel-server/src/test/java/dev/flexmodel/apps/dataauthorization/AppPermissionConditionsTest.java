package dev.flexmodel.apps.dataauthorization;

import dev.flexmodel.apps.form.dto.AppFormFieldResponse;
import dev.flexmodel.common.ValidationException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AppPermissionConditionsTest {
  private static final List<AppFormFieldResponse> FIELDS = List.of(
    new AppFormFieldResponse("name", "名称", "TEXT", false, false, null, null),
    new AppFormFieldResponse("code", "编码", "TEXT", false, false, null, null),
    new AppFormFieldResponse("amount", "金额", "DECIMAL", false, false, null, null),
    new AppFormFieldResponse("owner_id", "所有者", "REFERENCE", false, false, null, "f_user"));
  private static final List<AppFormFieldResponse> USER_FIELDS = List.of(
    new AppFormFieldResponse("id", "ID", "TEXT", true, true, null, null),
    new AppFormFieldResponse("dept", "部门", "TEXT", false, false, null, null),
    new AppFormFieldResponse("balance", "余额", "DECIMAL", false, false, null, null));
  private static final Function<String, List<AppFormFieldResponse>> REFERENCES = formName ->
    "f_user".equals(formName) ? USER_FIELDS : null;

  private static void validate(Map<String, Object> condition) {
    AppPermissionConditions.validate(condition, FIELDS, REFERENCES);
  }

  @Test
  void acceptsFilterDslConditions() {
    assertDoesNotThrow(() -> validate(null));
    assertDoesNotThrow(() -> validate(Map.of("name", "订单")));
    assertDoesNotThrow(() -> validate(Map.of(
      "name", Map.of("_eq", "订单"), "amount", Map.of("_gte", 10))));
    assertDoesNotThrow(() -> validate(Map.of("_or", List.of(
      Map.of("name", Map.of("_eq", "张三")), Map.of("name", Map.of("_eq", "李四"))))));
    assertDoesNotThrow(() -> validate(Map.of("amount", Map.of("_between", List.of(1, 100)))));
    assertDoesNotThrow(() -> validate(Map.of("name", Map.of("_contains", "张"))));
    Map<String, Object> eqNull = new HashMap<>();
    eqNull.put("_eq", null);
    assertDoesNotThrow(() -> validate(Map.of("amount", eqNull)));
  }

  @Test
  void acceptsDynamicValueTypes() {
    assertDoesNotThrow(() -> validate(Map.of("owner_id", Map.of("_eq", Map.of("_user", "id")))));
    assertDoesNotThrow(() -> validate(Map.of("name", Map.of("_ne", Map.of("_field", "code")))));
    assertDoesNotThrow(() -> validate(Map.of("owner_id", Map.of("_eq", Map.of("_field", "owner_id.id")))));
    assertDoesNotThrow(() -> validate(Map.of("name", Map.of("_eq", Map.of("_field", "owner_id.dept")))));
  }

  @Test
  void rejectsInvalidDynamicValueTypes() {
    for (var condition : List.<Map<String, Object>>of(
      Map.of("amount", Map.of("_eq", Map.of("_user", "id"))),
      Map.of("owner_id", Map.of("_eq", Map.of("_user", "name"))),
      Map.of("owner_id", Map.of("_gt", Map.of("_user", "id"))),
      Map.of("amount", Map.of("_eq", Map.of("_field", "name"))),
      Map.of("owner_id", Map.of("_eq", Map.of("_field", "ghost.id"))),
      Map.of("owner_id", Map.of("_eq", Map.of("_field", "name.id"))),
      Map.of("owner_id", Map.of("_eq", Map.of("_field", "owner_id.balance"))),
      Map.of("name", Map.of("_eq", Map.of("_field", "owner_id.dept.level"))),
      Map.of("name", Map.of("_eq", Map.of("_field", "")))))
      assertThrows(ValidationException.class, () -> validate(condition));
  }

  @Test
  void rejectsUnknownFieldsOperatorsAndInvalidTypes() {
    for (var condition : List.<Map<String, Object>>of(Map.of("sql", "1=1"), Map.of("missing", Map.of("_eq", "x")),
      Map.of("amount", Map.of("_eq", "10")), Map.of("name", Map.of("_gt", "x")),
      Map.of("name", Map.of("_like", "x")), Map.of("amount", Map.of("_eq", Map.of("_field", "name"))),
      Map.of("amount", Map.of("_contains", "1")), Map.of("amount", Map.of("_between", List.of(1)))))
      assertThrows(ValidationException.class, () -> validate(condition));
  }

  @Test
  void rejectsEmptyOrUnboundedTreesAndCollections() {
    assertThrows(ValidationException.class, () -> validate(Map.of()));
    assertThrows(ValidationException.class, () -> validate(Map.of("_or", List.of())));
    assertThrows(ValidationException.class, () -> validate(Map.of("name", Map.of())));
    assertThrows(ValidationException.class, () -> validate(Map.of("name", Map.of("_in", List.of()))));
    Map<String, Object> condition = Map.of("name", Map.of("_eq", "x"));
    for (int depth = 0; depth < 10; depth++) condition = Map.of("_and", List.of(condition));
    var tooDeep = condition;
    assertThrows(ValidationException.class, () -> validate(tooDeep));
  }
}
