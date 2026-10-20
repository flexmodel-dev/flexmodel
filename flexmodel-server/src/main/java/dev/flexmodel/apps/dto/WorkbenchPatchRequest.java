package dev.flexmodel.apps.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashSet;
import java.util.Set;

/**
 * 局部更新工作台请求。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkbenchPatchRequest {

  private String name;
  private String description;
  private WorkbenchLayoutRecord layout;
  private final Set<String> presentFields = new HashSet<>();

  @JsonProperty("name")
  public void setName(String name) {
    this.name = name;
    presentFields.add("name");
  }

  @JsonProperty("description")
  public void setDescription(String description) {
    this.description = description;
    presentFields.add("description");
  }

  @JsonProperty("layout")
  public void setLayout(WorkbenchLayoutRecord layout) {
    this.layout = layout;
    presentFields.add("layout");
  }

  public String name() {
    return name;
  }

  public String description() {
    return description;
  }

  public WorkbenchLayoutRecord layout() {
    return layout;
  }

  public boolean hasName() {
    return presentFields.contains("name");
  }

  public boolean hasDescription() {
    return presentFields.contains("description");
  }

  public boolean hasLayout() {
    return presentFields.contains("layout");
  }
}
