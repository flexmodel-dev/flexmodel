package dev.flexmodel.apps;

import dev.flexmodel.apps.dto.AppFormFieldResponse;
import dev.flexmodel.common.ValidationException;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import static dev.flexmodel.common.Values.*;

/**
 * 仅校验保存的配置条件，不编译或执行运行时数据过滤。
 */
public final class AppPermissionConditions {
  private AppPermissionConditions() {
  }

  private static final Set<String> OPERATORS = Set.of("EQ", "NE", "IN", "NOT_IN", "GT", "GE", "LT", "LE", "IS_NULL", "IS_NOT_NULL");

  public static void validate(Map<String, Object> node, List<AppFormFieldResponse> fields) {
    validate(node, fields, 0, new int[]{0});
  }

  private static void validate(Map<String, Object> node, List<AppFormFieldResponse> fields, int depth, int[] count) {
    if (node == null) return;
    if (depth > 8 || ++count[0] > 100) fail("权限条件过于复杂");
    if (node.containsKey("logic")) {
      keys(node, Set.of("logic", "children"));
      if (!Set.of("AND", "OR").contains(text(node, "logic")) || !(node.get("children") instanceof List<?> children) || children.isEmpty())
        fail("权限组合条件不合法");
      for (Object child : (List<?>) node.get("children")) {
        if (!(child instanceof Map<?, ?>)) fail("权限条件必须为对象");
        validate(object(child), fields, depth + 1, count);
      }
      return;
    }
    keys(node, Set.of("field", "operator", "value"));
    AppFormFieldResponse field = fields.stream().filter(f -> f.name().equals(text(node, "field")))
      .findFirst().orElseThrow(() -> new ValidationException("权限条件字段不存在"));
    String op = text(node, "operator");
    if (!OPERATORS.contains(op)) fail("权限条件操作符不支持");
    if (op.equals("IS_NULL") || op.equals("IS_NOT_NULL")) {
      if (node.get("value") != null) fail("空值操作不需要比较值");
      return;
    }
    Object value = node.get("value");
    if (op.equals("IN") || op.equals("NOT_IN")) {
      if (!(value instanceof List<?> list) || list.isEmpty() || list.size() > 200) fail("集合条件需要1至200个值");
      ((List<?>) value).forEach(item -> validateValue(field, item));
    } else {
      validateValue(field, value);
      if (!Set.of("EQ", "NE").contains(op) && !Set.of("NUMBER", "DECIMAL", "DATE", "DATETIME", "TIME").contains(field.type()))
        fail("该字段不支持大小比较");
    }
  }

  private static void validateValue(AppFormFieldResponse field, Object value) {
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
