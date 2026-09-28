package dev.flexmodel.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.flexmodel.JsonUtils;
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

    @JsonProperty("elements")
    public List<EnumElement> getElementDefinitions() {
        return elementDefinitions;
    }

    @JsonIgnore
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
    @JsonIgnore
    public List<String> getElements() {
        return elementDefinitions.stream().map(EnumElement::getName).toList();
    }

    @JsonProperty("elements")
    @JsonAlias("elementDefinitions")
    public EnumDefinition setElements(List<?> elements) {
        List<?> incoming = elements == null ? List.of() : elements;
        List<String> incomingNames = incoming.stream()
            .map(element -> element instanceof EnumElement enumElement ? enumElement.getName() : String.valueOf(element))
            .toList();
        if (!elementDefinitions.isEmpty() && getElements().equals(incomingNames)) {
            return this;
        }
        this.elementDefinitions = new ArrayList<>();
        for (Object element : incoming) {
            if (element instanceof EnumElement enumElement) {
                this.elementDefinitions.add(enumElement);
            } else if (element instanceof String name) {
                this.elementDefinitions.add(new EnumElement(name));
            } else if (element != null) {
                this.elementDefinitions.add(JsonUtils.convertValue(element, EnumElement.class));
            }
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
