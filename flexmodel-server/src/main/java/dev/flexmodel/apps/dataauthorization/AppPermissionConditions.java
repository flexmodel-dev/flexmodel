package dev.flexmodel.apps.dataauthorization;

import dev.flexmodel.apps.form.dto.AppFormFieldResponse;
import dev.flexmodel.common.ValidationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static dev.flexmodel.common.Values.object;

/**
 * 仅校验保存的配置条件，不编译或执行运行时数据过滤。
 * 条件语法复用记录查询 filter DSL（见 flexmodel-website/docs/tutorial/features/records.md）：
 * 同层字段隐式 AND、{"字段": 值} 等价 _eq、_and/_or 逻辑组合。
 * 值类型支持：自定义字面量、当前用户 {"_user": "id"}、字段引用 {"_field": "路径"}
 * （本表裸路径或 "引用字段.目标字段" 一级路径）；后两者仅允许 _eq/_ne。
 */
public final class AppPermissionConditions {
  private AppPermissionConditions() {
  }

  private static final Set<String> OPERATORS = Set.of("_eq", "_ne", "_gt", "_gte", "_lt", "_lte",
    "_contains", "_not_contains", "_starts_with", "_ends_with", "_in", "_nin", "_between");
  private static final Set<String> COMPARABLE_TYPES = Set.of("NUMBER", "DECIMAL", "DATE", "DATETIME", "TIME");
  private static final Set<String> TEXTUAL_OPERATORS = Set.of("_contains", "_not_contains", "_starts_with", "_ends_with");
  private static final Set<String> ORDERING_OPERATORS = Set.of("_gt", "_gte", "_lt", "_lte");
  private static final Set<String> USER_VALUE_TYPES = Set.of("REFERENCE", "TEXT");

  public static void validate(Map<String, Object> node, List<AppFormFieldResponse> fields,
                              Function<String, List<AppFormFieldResponse>> referenceFields) {
    validateNode(node, fields, referenceFields, 0, new int[]{0});
  }

  private static void validateNode(Map<String, Object> node, List<AppFormFieldResponse> fields,
                                   Function<String, List<AppFormFieldResponse>> referenceFields, int depth, int[] count) {
    if (node == null) return;
    if (node.isEmpty()) fail("权限条件不能为空对象");
    if (depth > 8 || ++count[0] > 100) fail("权限条件过于复杂");
    for (Map.Entry<String, Object> entry : node.entrySet()) {
      String key = entry.getKey();
      if ("_and".equals(key) || "_or".equals(key)) {
        if (!(entry.getValue() instanceof List<?> children) || children.isEmpty()) fail("逻辑组合条件需要非空数组");
        for (Object child : (List<?>) entry.getValue()) {
          if (!(child instanceof Map<?, ?>)) fail("逻辑组合条件元素必须为对象");
          validateNode(object(child), fields, referenceFields, depth + 1, count);
        }
        continue;
      }
      if (key.startsWith("_")) fail("权限条件操作符不支持");
      AppFormFieldResponse field = resolve(fields, key);
      Object value = entry.getValue();
      if (!(value instanceof Map<?, ?> operators)) {
        validateValue(field, "_eq", value, fields, referenceFields);
        continue;
      }
      if (operators.isEmpty()) fail("权限条件不能为空对象");
      for (Map.Entry<?, ?> operator : operators.entrySet()) {
        String name = String.valueOf(operator.getKey());
        if (!name.startsWith("_")) fail("权限条件不支持关联路径: " + key);
        if (!OPERATORS.contains(name)) fail("权限条件操作符不支持");
        validateValue(field, name, operator.getValue(), fields, referenceFields);
      }
    }
  }

  private static AppFormFieldResponse resolve(List<AppFormFieldResponse> fields, String name) {
    return fields.stream().filter(f -> f.name().equals(name))
      .findFirst().orElseThrow(() -> new ValidationException("权限条件字段不存在"));
  }

  private static void validateValue(AppFormFieldResponse field, String operator, Object value,
                                    List<AppFormFieldResponse> fields, Function<String, List<AppFormFieldResponse>> referenceFields) {
    if (TEXTUAL_OPERATORS.contains(operator) && (COMPARABLE_TYPES.contains(field.type()) || "BOOL".equals(field.type())))
      fail("该字段不支持文本匹配");
    if (value instanceof Map<?, ?> operand && (operand.containsKey("_field") || operand.containsKey("_user"))) {
      validateOperand(field, operator, operand, fields, referenceFields);
      return;
    }
    if (operator.equals("_in") || operator.equals("_nin")) {
      if (!(value instanceof List<?> list) || list.isEmpty() || list.size() > 200) fail("集合条件需要1至200个值");
      ((List<?>) value).forEach(item -> validateScalar(field, item));
      return;
    }
    if (operator.equals("_between")) {
      if (!(value instanceof List<?> list) || list.size() != 2) fail("范围条件需要两个比较值");
      requireComparable(field);
      ((List<?>) value).forEach(item -> validateScalar(field, item));
      return;
    }
    if (ORDERING_OPERATORS.contains(operator)) requireComparable(field);
    if (value == null) {
      if (!operator.equals("_eq") && !operator.equals("_ne")) fail("该操作符不支持空值");
      return;
    }
    validateScalar(field, value);
  }

  private static void validateOperand(AppFormFieldResponse field, String operator, Map<?, ?> operand,
                                      List<AppFormFieldResponse> fields, Function<String, List<AppFormFieldResponse>> referenceFields) {
    if (!operator.equals("_eq") && !operator.equals("_ne")) fail("动态值仅支持等于或不等于");
    if (operand.size() != 1) fail("动态值不合法");
    if (operand.containsKey("_user")) {
      if (!"id".equals(operand.get("_user")) || !USER_VALUE_TYPES.contains(field.type()))
        fail("当前用户值仅支持文本或引用字段的 id");
      return;
    }
    if (!(operand.get("_field") instanceof String path) || path.isBlank()) fail("字段引用路径不合法");
    String reference = (String) operand.get("_field");
    int dot = reference.indexOf('.');
    if (dot < 0) {
      requireCompatible(field, resolve(fields, reference));
      return;
    }
    String refName = reference.substring(0, dot);
    String targetName = reference.substring(dot + 1);
    if (targetName.isEmpty() || targetName.contains(".")) fail("字段引用不支持多级路径");
    AppFormFieldResponse ref = resolve(fields, refName);
    if (!"REFERENCE".equals(ref.type()) || ref.referenceForm() == null || ref.referenceForm().isBlank())
      fail("字段引用需要通过引用字段访问他表字段");
    List<AppFormFieldResponse> targetFields = referenceFields.apply(ref.referenceForm());
    if (targetFields == null) fail("引用表单不存在: " + ref.referenceForm());
    requireCompatible(field, resolve(targetFields, targetName));
  }

  private static void requireCompatible(AppFormFieldResponse left, AppFormFieldResponse right) {
    boolean sameType = left.type().equals(right.type());
    boolean leftRef = "REFERENCE".equals(left.type())
      && ("TEXT".equals(right.type()) || right.builtin() && "id".equals(right.name()));
    boolean rightRef = "REFERENCE".equals(right.type())
      && ("TEXT".equals(left.type()) || left.builtin() && "id".equals(left.name()));
    if (!sameType && !leftRef && !rightRef) fail("字段引用与字段类型不匹配");
  }

  private static void requireComparable(AppFormFieldResponse field) {
    if (!COMPARABLE_TYPES.contains(field.type())) fail("该字段不支持大小比较");
  }

  private static void validateScalar(AppFormFieldResponse field, Object value) {
    if (value == null || value instanceof Map<?, ?> || value instanceof Collection<?>)
      fail("权限条件比较值不能为空或对象");
    try {
      switch (field.type()) {
        case "NUMBER", "DECIMAL" -> {
          if (!(value instanceof Number)) fail("数字条件需要数字");
          new BigDecimal(value.toString());
        }
        case "BOOL" -> {
          if (!(value instanceof Boolean)) fail("布尔条件需要布尔值");
        }
        case "DATE" -> LocalDate.parse((String) value);
        case "DATETIME" -> LocalDateTime.parse((String) value);
        case "TIME" -> LocalTime.parse((String) value);
        default -> {
          if (!(value instanceof String)) fail("文本条件需要字符串");
        }
      }
    } catch (ClassCastException | java.time.format.DateTimeParseException | NumberFormatException error) {
      fail("权限条件比较值与字段类型不匹配");
    }
  }

  public static void keys(Map<String, ?> value, Set<String> allowed) {
    if (!allowed.containsAll(value.keySet())) fail("权限配置包含未知属性");
  }

  private static void fail(String message) {
    throw new ValidationException(message);
  }
}
