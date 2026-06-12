package dev.flexmodel.supports.jackson;

import com.fasterxml.jackson.databind.PropertyName;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.AnnotatedField;
import com.fasterxml.jackson.databind.introspect.AnnotatedMethod;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import dev.flexmodel.annotation.ModelField;

import java.util.ArrayList;
import java.util.List;

/**
 * 自定义注解解析器，优先使用 {@link ModelField} 的值作为字段名称。
 * <p>
 * 序列化时使用 @ModelField 名称（保证 API 响应和 Map 转换使用数据库列名），
 * 反序列化时除 @ModelField 名称外，同时将标准 Java 驼峰名作为别名，
 * 以兼容 Java 对象间转换（如 DTO → Entity）时名称不匹配的问题。
 *
 * @author cjbi
 */
public class ModelFieldAnnotationIntrospector extends JacksonAnnotationIntrospector {

  @Override
  public PropertyName findNameForSerialization(Annotated annotated) {
    PropertyName name = super.findNameForSerialization(annotated);
    if (name != null && !name.isEmpty()) {
      return name;
    }
    return findModelFieldName(annotated);
  }

  @Override
  public PropertyName findNameForDeserialization(Annotated annotated) {
    PropertyName name = super.findNameForDeserialization(annotated);
    if (name != null && !name.isEmpty()) {
      return name;
    }
    return findModelFieldName(annotated);
  }

  /**
   * 为带有 @ModelField 注解的属性添加标准 Java 驼峰名作为反序列化别名，
   * 使得 {@code JsonUtils.convertValue(DTO, Entity.class)} 时，
   * DTO 序列化的驼峰名能被实体正确接受。
   */
  @Override
  public List<PropertyName> findPropertyAliases(Annotated a) {
    List<PropertyName> fromSuper = super.findPropertyAliases(a);
    PropertyName modelFieldName = findModelFieldName(a);
    if (modelFieldName == null) {
      return fromSuper;
    }
    String javaName = resolveJavaPropertyName(a);
    if (javaName != null && !javaName.equals(modelFieldName.getSimpleName())) {
      List<PropertyName> aliases = new ArrayList<>();
      if (fromSuper != null) {
        aliases.addAll(fromSuper);
      }
      aliases.add(PropertyName.construct(javaName));
      return aliases;
    }
    return fromSuper;
  }

  /**
   * 从 Annotated 成员推导标准 Java Bean 属性名（驼峰命名）。
   */
  private String resolveJavaPropertyName(Annotated a) {
    if (a instanceof AnnotatedField f) {
      return f.getName();
    }
    if (a instanceof AnnotatedMethod m) {
      String rawName = m.getName();
      if (rawName.startsWith("get") && rawName.length() > 3) {
        return decapitalize(rawName.substring(3));
      } else if (rawName.startsWith("set") && rawName.length() > 3) {
        return decapitalize(rawName.substring(3));
      } else if (rawName.startsWith("is") && rawName.length() > 2) {
        return decapitalize(rawName.substring(2));
      }
    }
    return null;
  }

  private String decapitalize(String name) {
    if (name == null || name.isEmpty()) {
      return name;
    }
    if (name.length() > 1 && Character.isUpperCase(name.charAt(1))) {
      return name; // 保留首字母缩写词如 "URL"
    }
    return Character.toLowerCase(name.charAt(0)) + name.substring(1);
  }

  private PropertyName findModelFieldName(Annotated annotated) {
    ModelField modelField = _findAnnotation(annotated, ModelField.class);
    if (modelField != null) {
      String value = modelField.value();
      if (value != null && !value.isEmpty()) {
        return PropertyName.construct(value);
      }
    }
    return null;
  }
}


