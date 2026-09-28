package dev.flexmodel.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author cjbi
 */
public class EnumDefinition implements SchemaObject {

    private String name;
    private String comment;
    private List<EnumElement> elementDefinitions = new ArrayList<>();
    protected Map<String, Object> additionalProperties = new HashMap<>();

    public EnumDefinition(String name) {
        this.name = name;
    }

    public EnumDefinition setName(String name) {
        this.name = name;
        return this;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getType() {
      return "Enum";
    }

    public List<EnumElement> getElementDefinitions() {
        return elementDefinitions;
    }

    public EnumDefinition setElementDefinitions(List<EnumElement> elementDefinitions) {
        this.elementDefinitions = elementDefinitions == null ? new ArrayList<>() : new ArrayList<>(elementDefinitions);
        return this;
    }

    public EnumDefinition addElementDefinition(EnumElement element) {
        elementDefinitions.add(element);
        return this;
    }

    /**
     * 元素名列表（向后兼容视图）。
     */
    public List<String> getElements() {
        return elementDefinitions.stream().map(EnumElement::getName).toList();
    }

    public EnumDefinition setElements(List<String> elements) {
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

    public EnumDefinition addElement(String element) {
        elementDefinitions.add(new EnumElement(element));
        return this;
    }

    public String getComment() {
        return comment;
    }

    public EnumDefinition setComment(String comment) {
        this.comment = comment;
        return this;
    }

    public Map<String, Object> getAdditionalProperties() {
        return additionalProperties;
    }

    public void setAdditionalProperties(Map<String, Object> additionalProperties) {
        this.additionalProperties = additionalProperties;
    }

    public EnumDefinition addAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        if (this.getName() != null && obj instanceof EntityDefinition) {
            return this.getName().equals(((EntityDefinition) obj).getName());
        }
        return false;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "<" + getName() + ">";
    }

}
