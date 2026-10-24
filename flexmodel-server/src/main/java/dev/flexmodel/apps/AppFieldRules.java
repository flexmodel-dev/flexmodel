package dev.flexmodel.apps;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.apps.dto.AppFieldConfig;
import dev.flexmodel.apps.dto.AppFormFieldOptionResponse;
import dev.flexmodel.apps.dto.AppFormFieldResponse;
import dev.flexmodel.common.ValidationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Apps 字段配置与记录写入共用的规则，避免绕过表单后约束失效。 */
public final class AppFieldRules {
  private static final Set<String> TEXT = Set.of("TEXT", "NTEXT", "PHONE", "EMAIL", "URL");
  private static final Set<String> NUMERIC = Set.of("NUMBER", "DECIMAL");
  private static final Set<String> TEMPORAL = Set.of("DATE", "DATETIME", "TIME");
  private static final Set<String> CHOICE = Set.of("PICKLIST", "SELECT", "CLASSIFICATION", "MULTISELECT");
  private static final Map<String, Set<String>> DATE_FORMATS = Map.of(
    "DATE", Set.of("yyyy-MM-dd", "yyyy/MM/dd", "yyyy-MM", "yyyy"),
    "DATETIME", Set.of("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy/MM/dd HH:mm"),
    "TIME", Set.of("HH:mm:ss", "HH:mm"));

  private AppFieldRules() {}

  public static AppFieldConfig validateConfig(String type, AppFieldConfig config) {
    if (config == null) return null;
    Set<String> allowed = new HashSet<>(Set.of("creatable", "updatable"));
    if (TEXT.contains(type) || NUMERIC.contains(type) || TEMPORAL.contains(type) || "BOOL".equals(type)) {
      allowed.add("defaultValue");
    }
    if (TEXT.contains(type)) allowed.add("maxLength");
    if (Set.of("TEXT", "NTEXT").contains(type)) {
      allowed.addAll(Set.of("commonValues", "pattern", "validationMessage"));
    }
    if (NUMERIC.contains(type)) {
      allowed.addAll(Set.of("allowNegative", "thousandsSeparator", "prefix", "suffix"));
    }
    if ("DECIMAL".equals(type)) allowed.add("decimalPlaces");
    if (TEMPORAL.contains(type)) allowed.addAll(Set.of("defaultNow", "dateFormat"));
    if (Set.of("PICKLIST", "SELECT", "MULTISELECT").contains(type)) allowed.add("displayStyle");
    Map<String, Object> properties = JsonUtils.convertValue(config, Map.class);
    properties.forEach((key, value) -> {
      if (value != null && !allowed.contains(key)) fail("字段类型 " + type + " 不支持属性: " + key);
    });
    if (config.getMaxLength() != null) {
      int capacity = switch (type) {
        case "NTEXT" -> 4000;
        case "PHONE", "EMAIL", "URL" -> 512;
        default -> 255;
      };
      if (config.getMaxLength() < 1 || config.getMaxLength() > capacity) fail("最大长度应在 1 到 " + capacity + " 之间");
    }
    if (config.getPattern() != null && !config.getPattern().isBlank()) {
      if (config.getPattern().length() > 500) fail("格式验证表达式不能超过500个字符");
      try {
        Pattern.compile(config.getPattern());
      } catch (PatternSyntaxException exception) {
        fail("格式验证正则表达式不合法");
      }
    }
    if (config.getCommonValues() != null) {
      if (config.getCommonValues().size() > 100) fail("常用值不能超过100项");
      config.getCommonValues().forEach(value -> validateValue(type, "常用值", config, null, value));
    }
    if (config.getDecimalPlaces() != null && (config.getDecimalPlaces() < 0 || config.getDecimalPlaces() > 6)) {
      fail("小数位数应在 0 到 6 之间");
    }
    if (config.getPrefix() != null && config.getPrefix().length() > 20
      || config.getSuffix() != null && config.getSuffix().length() > 20) fail("数字前缀和后缀不能超过20个字符");
    if (config.getDateFormat() != null && !DATE_FORMATS.getOrDefault(type, Set.of()).contains(config.getDateFormat())) {
      fail("日期显示格式不合法");
    }
    if (Boolean.TRUE.equals(config.getDefaultNow()) && config.getDefaultValue() != null) {
      fail("当前时间默认值与固定默认值不能同时设置");
    }
    if (config.getDisplayStyle() != null) {
      Set<String> styles = "MULTISELECT".equals(type) ? Set.of("CHECKBOX", "DROPDOWN") : Set.of("RADIO", "DROPDOWN");
      if (!styles.contains(config.getDisplayStyle())) fail("选项显示样式不合法");
    }
    validateValue(type, "默认值", config, null, config.getDefaultValue());
    return config;
  }

  public static boolean writable(AppFormFieldResponse field, boolean creating) {
    AppFieldConfig config = field.config();
    return config == null || !Boolean.FALSE.equals(creating ? config.getCreatable() : config.getUpdatable());
  }

  public static Object defaultValue(AppFormFieldResponse field) {
    AppFieldConfig config = field.config();
    if (config != null) {
      if (Boolean.TRUE.equals(config.getDefaultNow())) return switch (field.type()) {
        case "DATE" -> LocalDate.now().toString();
        case "TIME" -> LocalTime.now().withNano(0).toString();
        default -> LocalDateTime.now().withNano(0).toString();
      };
      if (config.getDefaultValue() != null) return config.getDefaultValue();
    }
    if (field.options() != null) {
      List<String> defaults = field.options().stream().filter(option -> Boolean.TRUE.equals(option.defaultSelected()))
        .map(AppFormFieldOptionResponse::id).toList();
      if ("MULTISELECT".equals(field.type())) return defaults.isEmpty() ? null : defaults;
      if (!defaults.isEmpty()) return defaults.getFirst();
    }
    return null;
  }

  public static Object validateValue(AppFormFieldResponse field, Object value) {
    return validateValue(field.type(), field.label(), field.config(), field.options(), value);
  }

  private static Object validateValue(String type, String label, AppFieldConfig config,
                                    List<AppFormFieldOptionResponse> options, Object value) {
    if (isEmpty(value)) return value;
    String text = String.valueOf(value);
    if (TEXT.contains(type)) {
      if (!(value instanceof String)) fail(label + "必须是文本");
      int capacity = "NTEXT".equals(type) ? 4000 : "TEXT".equals(type) ? 255 : 512;
      int maxLength = config != null && config.getMaxLength() != null ? config.getMaxLength() : capacity;
      if (text.length() > maxLength) fail(label + "不能超过" + maxLength + "个字符");
      if (config != null && config.getPattern() != null && !config.getPattern().isBlank()
        && !Pattern.matches(config.getPattern(), text)) {
        fail(config.getValidationMessage() == null || config.getValidationMessage().isBlank()
          ? label + "格式不正确" : config.getValidationMessage());
      }
      if ("EMAIL".equals(type) && !text.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) fail(label + "邮箱格式不正确");
      if ("URL".equals(type) && !text.matches("(?i)^(https?|ftp)://[^\\s/]+(?:/[^\\s]*)?$")) fail(label + "链接格式不正确");
      if ("PHONE".equals(type) && !text.matches("^\\+?[0-9() -]{3,40}$")) fail(label + "电话格式不正确");
    }
    if (NUMERIC.contains(type)) {
      BigDecimal number;
      try {
        number = new BigDecimal(text);
      } catch (NumberFormatException exception) {
        throw new ValidationException(label + "必须是有效数字");
      }
      if ("NUMBER".equals(type) && number.stripTrailingZeros().scale() > 0) fail(label + "必须是整数");
      if ("NUMBER".equals(type) && (number.compareTo(BigDecimal.valueOf(Integer.MIN_VALUE)) < 0
        || number.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0)) fail(label + "超出整数范围");
      if (!Double.isFinite(number.doubleValue())) fail(label + "超出数字范围");
      if (config != null && Boolean.FALSE.equals(config.getAllowNegative()) && number.signum() < 0) fail(label + "不允许负数");
      if (config != null && config.getDecimalPlaces() != null
        && number.stripTrailingZeros().scale() > config.getDecimalPlaces()) fail(label + "最多允许" + config.getDecimalPlaces() + "位小数");
      return number;
    }
    if ("BOOL".equals(type) && !(value instanceof Boolean)) fail(label + "必须是布尔值");
    if (TEMPORAL.contains(type)) {
      try {
        switch (type) {
          case "DATE" -> LocalDate.parse(text);
          case "TIME" -> LocalTime.parse(text);
          default -> LocalDateTime.parse(text.replace(' ', 'T'));
        }
      } catch (DateTimeParseException exception) {
        fail(label + "日期或时间格式不正确");
      }
    }
    if (CHOICE.contains(type) && options != null) {
      Set<String> ids = new HashSet<>();
      options.forEach(option -> ids.add(option.id()));
      if ("MULTISELECT".equals(type)) {
        if (!(value instanceof List<?>)) fail(label + "必须是选项数组");
        List<?> selected = (List<?>) value;
        if (selected.stream().anyMatch(item -> !ids.contains(item))) fail(label + "包含无效选项");
        return selected.stream().distinct().toList();
      }
      if (!ids.contains(value)) fail(label + "包含无效选项");
    }
    return value;
  }

  public static boolean isEmpty(Object value) {
    return value == null || value instanceof String text && text.isBlank()
      || value instanceof Collection<?> collection && collection.isEmpty();
  }

  private static void fail(String message) { throw new ValidationException(message); }
}
