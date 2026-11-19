package dev.flexmodel.apps.record;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.apps.form.dto.AppFormFieldResponse;
import dev.flexmodel.apps.form.dto.AppFormResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 记录历史差异计算：基于实际保存前与保存后的记录计算字段变化。
 * <p>
 * 规则：null、空串、0、false 语义不同；数值按标量比较；标签类无序字段重排不产生变化；
 * JSON 值忽略键顺序；系统维护字段不参与差异。
 */
final class AppRecordHistoryDiff {

  private static final Set<String> SYSTEM_FIELDS = Set.of(
    "id", "owner_id", "created_by", "updated_by", "created_at", "updated_at");
  private static final Set<String> UNORDERED_LIST_TYPES = Set.of("TAG");
  private static final Set<String> ATTACHMENT_TYPES = Set.of("FILE", "IMAGE", "AVATAR");
  private static final int MAX_DISPLAY_LENGTH = 200;

  private AppRecordHistoryDiff() {
  }

  /**
   * 计算修改前后差异；无实际业务变化时返回空列表。
   */
  static List<Map<String, Object>> changes(
    AppFormResponse form,
    Map<String, Object> before,
    Map<String, Object> after
  ) {
    List<Map<String, Object>> result = new ArrayList<>();
    for (AppFormFieldResponse field : form.fields()) {
      if (field.builtin() || SYSTEM_FIELDS.contains(field.name())) {
        continue;
      }
      Object oldValue = before == null ? null : before.get(field.name());
      Object newValue = after == null ? null : after.get(field.name());
      if (equals(field, oldValue, newValue)) {
        continue;
      }
      result.add(change(field, oldValue, newValue));
    }
    return result;
  }

  /**
   * 创建事件的初始值清单：只包含实际写入的业务字段。
   */
  static List<Map<String, Object>> initialValues(AppFormResponse form, Map<String, Object> values) {
    List<Map<String, Object>> result = new ArrayList<>();
    if (values == null) {
      return result;
    }
    for (AppFormFieldResponse field : form.fields()) {
      if (field.builtin() || SYSTEM_FIELDS.contains(field.name())) {
        continue;
      }
      if (!values.containsKey(field.name()) || values.get(field.name()) == null) {
        continue;
      }
      result.add(change(field, null, values.get(field.name())));
    }
    return result;
  }

  private static Map<String, Object> change(AppFormFieldResponse field, Object before, Object after) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("fieldName", field.name());
    result.put("fieldLabel", field.label());
    result.put("fieldType", field.type());
    result.put("before", serializable(before));
    result.put("after", serializable(after));
    result.put("beforeDisplay", display(field, before));
    result.put("afterDisplay", display(field, after));
    return result;
  }

  private static boolean equals(AppFormFieldResponse field, Object before, Object after) {
    if (before == null && after == null) {
      return true;
    }
    if (before == null || after == null) {
      return false;
    }
    String type = field.type() == null ? "" : field.type();
    if (Set.of("NUMBER", "DECIMAL").contains(type)) {
      return numeric(before) != null && numeric(after) != null
        && numeric(before).compareTo(numeric(after)) == 0;
    }
    if ("BOOL".equals(type)) {
      return Boolean.parseBoolean(String.valueOf(before)) == Boolean.parseBoolean(String.valueOf(after));
    }
    if (before instanceof List<?> beforeList && after instanceof List<?> afterList) {
      if (UNORDERED_LIST_TYPES.contains(type)) {
        return new LinkedHashSet<>(toStringList(beforeList)).equals(new LinkedHashSet<>(toStringList(afterList)));
      }
      return toStringList(beforeList).equals(toStringList(afterList));
    }
    if (isJsonLike(type) || before instanceof Map || after instanceof Map) {
      return jsonNormalized(before).equals(jsonNormalized(after));
    }
    if (Set.of("DATE", "DATETIME", "TIME").contains(type)) {
      return temporal(before).equals(temporal(after));
    }
    return serializeComparable(before).equals(serializeComparable(after));
  }

  private static String display(AppFormFieldResponse field, Object value) {
    if (value == null) {
      return null;
    }
    String type = field.type() == null ? "" : field.type();
    if (value instanceof List<?> values) {
      List<String> parts = new ArrayList<>();
      for (Object item : values) {
        String rendered = item instanceof Map<?, ?> map && ATTACHMENT_TYPES.contains(type)
          ? Objects.toString(map.get("name"), null)
          : String.valueOf(item);
        if (rendered != null && !rendered.isBlank()) {
          parts.add(rendered);
        }
      }
      return parts.isEmpty() ? null : truncate(String.join("、", parts));
    }
    if (ATTACHMENT_TYPES.contains(type) && value instanceof String text && text.startsWith("[")) {
      List<String> names = attachmentNames(text);
      if (!names.isEmpty()) {
        return truncate(String.join("、", names));
      }
    }
    if ("BOOL".equals(type)) {
      return Boolean.parseBoolean(String.valueOf(value)) ? "是" : "否";
    }
    if (value instanceof LocalDateTime time) {
      return time.toString();
    }
    return truncate(String.valueOf(value));
  }

  private static List<String> attachmentNames(String json) {
    try {
      List<String> names = new ArrayList<>();
      for (Map<String, Object> item : JsonUtils.parseToMapList(json)) {
        String name = Objects.toString(item.get("name"), null);
        if (name != null && !name.isBlank()) {
          names.add(name);
        }
      }
      return names;
    } catch (RuntimeException error) {
      return List.of();
    }
  }

  private static BigDecimal numeric(Object value) {
    try {
      if (value instanceof Number number) {
        return new BigDecimal(number.toString());
      }
      return new BigDecimal(String.valueOf(value).trim());
    } catch (RuntimeException error) {
      return null;
    }
  }

  private static String temporal(Object value) {
    if (value instanceof LocalDateTime time) {
      return time.toString();
    }
    return String.valueOf(value).trim();
  }

  private static boolean isJsonLike(String type) {
    return ATTACHMENT_TYPES.contains(type) || Set.of("LOCATION", "SIGN", "BARCODE", "CLASSIFICATION").contains(type);
  }

  private static String jsonNormalized(Object value) {
    if (value == null) {
      return "";
    }
    if (value instanceof String text) {
      String trimmed = text.trim();
      if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
        try {
          return JsonUtils.toJsonString(canonicalize(JsonUtils.parseToObject(trimmed, Object.class)));
        } catch (RuntimeException ignored) {
          return trimmed;
        }
      }
      return trimmed;
    }
    try {
      return JsonUtils.toJsonString(canonicalize(value));
    } catch (RuntimeException error) {
      return String.valueOf(value);
    }
  }

  /**
   * 规范化 JSON 值：对象键排序，消除键顺序差异。
   */
  private static Object canonicalize(Object value) {
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> sorted = new java.util.TreeMap<>();
      map.forEach((key, entryValue) -> sorted.put(String.valueOf(key), canonicalize(entryValue)));
      return sorted;
    }
    if (value instanceof List<?> list) {
      return list.stream().map(AppRecordHistoryDiff::canonicalize).toList();
    }
    return value;
  }

  private static Object serializable(Object value) {
    if (value instanceof LocalDateTime time) {
      return time.toString();
    }
    if (value instanceof BigDecimal decimal) {
      return decimal.toPlainString();
    }
    return value;
  }

  private static String serializeComparable(Object value) {
    return value instanceof LocalDateTime time ? time.toString() : String.valueOf(value);
  }

  private static List<String> toStringList(List<?> values) {
    return values.stream().map(AppRecordHistoryDiff::serializeComparable).toList();
  }

  private static String truncate(String value) {
    return value.length() <= MAX_DISPLAY_LENGTH ? value : value.substring(0, MAX_DISPLAY_LENGTH) + "…";
  }
}
