package dev.flexmodel.codegen;

import dev.flexmodel.model.EnumDefinition;
import dev.flexmodel.model.EnumElement;

import java.util.ArrayList;
import java.util.List;

/**
 * @author cjbi
 */
public class EnumClass extends AbstractClass<EnumClass> {

  private List<EnumElement> elementDefinitions = new ArrayList<>();
  private EnumDefinition original;

  public static EnumClass buildEnumClass(String packageName, String schemaName, EnumDefinition anEnum) {
    String ftName = StringUtils.capitalize(StringUtils.snakeToCamel(anEnum.getName()));

    EnumClass enumClass = new EnumClass()
      .setSchemaName(schemaName)
      .setPackageName(packageName + ".enumeration")
      .setShortClassName(ftName)
      .setName(anEnum.getName());

    enumClass.setFullClassName(enumClass.getPackageName() + "." + ftName);
    enumClass.setVariableName(StringUtils.uncapitalize(ftName));
    enumClass.setElementDefinitions(anEnum.getElements());
    enumClass.setComment(anEnum.getComment());
    enumClass.setOriginal(anEnum);
    return enumClass;
  }

  public List<EnumElement> getElementDefinitions() {
    return elementDefinitions;
  }

  public EnumClass setElementDefinitions(List<EnumElement> elementDefinitions) {
    this.elementDefinitions = elementDefinitions == null ? new ArrayList<>() : new ArrayList<>(elementDefinitions);
    return this;
  }

  /**
   * 元素名列表（向后兼容视图，GraphQL Schema 生成等仍在使用）。
   */
  public List<String> getElements() {
    return elementDefinitions.stream().map(EnumElement::getName).toList();
  }

  public EnumClass setElements(List<String> elements) {
    List<String> incoming = elements == null ? List.of() : elements;
    if (!elementDefinitions.isEmpty() && getElements().equals(incoming)) {
      return this;
    }
    this.elementDefinitions = new ArrayList<>();
    for (String element : incoming) {
      this.elementDefinitions.add(new EnumElement(element));
    }
    return this;
  }

  public EnumDefinition getOriginal() {
    return original;
  }

  public EnumClass setOriginal(EnumDefinition original) {
    this.original = original;
    return this;
  }
}