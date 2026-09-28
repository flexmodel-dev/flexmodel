package dev.flexmodel.type;

import dev.flexmodel.ModelRegistry;
import dev.flexmodel.model.EnumDefinition;
import dev.flexmodel.model.EnumElement;
import dev.flexmodel.model.field.EnumRefField;
import dev.flexmodel.model.field.Field;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * 枚举类型处理器：API 层使用元素名，落库时翻译为 @code 声明的编码。
 * 未声明编码或无法解析枚举定义时保持原值透传（向后兼容）。
 *
 * @author cjbi
 */
public class EnumTypeHandler implements TypeHandler<Object> {

  private final ModelRegistry modelRegistry;
  private final String schemaName;

  public EnumTypeHandler() {
    this(null, null);
  }

  public EnumTypeHandler(ModelRegistry modelRegistry, String schemaName) {
    this.modelRegistry = modelRegistry;
    this.schemaName = schemaName;
  }

  @Override
  public Object convertParameter(Field field, Object value) {
    if (value == null) {
      return null;
    }
    if (!(field instanceof EnumRefField enumField) || modelRegistry == null) {
      return value;
    }
    EnumDefinition enumDefinition = resolveEnum(enumField);
    if (!hasCodes(enumDefinition)) {
      return value;
    }
    if (enumField.isMultiple()) {
      List<Object> codes = new ArrayList<>();
      for (Object item : toCollection(value)) {
        codes.add(toCode(enumDefinition, item));
      }
      return codes;
    }
    return toCode(enumDefinition, value);
  }

  /**
   * 读取方向：将落库编码翻译回元素名。
   */
  public Object getResult(Field field, Object value) {
    if (value == null) {
      return null;
    }
    if (!(field instanceof EnumRefField enumField) || modelRegistry == null) {
      return value;
    }
    EnumDefinition enumDefinition = resolveEnum(enumField);
    if (!hasCodes(enumDefinition)) {
      return value;
    }
    if (enumField.isMultiple()) {
      List<Object> names = new ArrayList<>();
      for (Object item : toCollection(value)) {
        names.add(toName(enumDefinition, item));
      }
      return names;
    }
    return toName(enumDefinition, value);
  }

  private EnumDefinition resolveEnum(EnumRefField field) {
    Object registered = modelRegistry.getRegistered(schemaName, field.getFrom());
    return registered instanceof EnumDefinition definition ? definition : null;
  }

  private static boolean hasCodes(EnumDefinition enumDefinition) {
    return enumDefinition != null
      && enumDefinition.getElements().stream().anyMatch(e -> e.getCode() != null);
  }

  private static Collection<?> toCollection(Object value) {
    if (value instanceof Collection<?> collection) {
      return collection;
    }
    if (value instanceof Object[] array) {
      return List.of(array);
    }
    return List.of(value);
  }

  private static Object toCode(EnumDefinition enumDefinition, Object value) {
    String name = value instanceof Enum<?> e ? e.name() : Objects.toString(value, null);
    if (name == null) {
      return null;
    }
    for (EnumElement element : enumDefinition.getElements()) {
      if (element.getName().equals(name)) {
        Object code = element.getCode();
        return code != null ? code : name;
      }
    }
    throw new IllegalArgumentException(
      "Unknown value '" + name + "' for enum " + enumDefinition.getName());
  }

  private static Object toName(EnumDefinition enumDefinition, Object storedValue) {
    String key = Objects.toString(storedValue, null);
    if (key == null) {
      return null;
    }
    for (EnumElement element : enumDefinition.getElements()) {
      if (element.getCode() != null && String.valueOf(element.getCode()).equals(key)) {
        return element.getName();
      }
    }
    // 兼容声明编码前按名字落库的存量数据
    for (EnumElement element : enumDefinition.getElements()) {
      if (element.getName().equals(key)) {
        return key;
      }
    }
    throw new IllegalArgumentException(
      "Unknown code '" + key + "' for enum " + enumDefinition.getName());
  }
}