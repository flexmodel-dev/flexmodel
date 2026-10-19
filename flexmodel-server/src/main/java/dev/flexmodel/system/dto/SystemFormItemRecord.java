package dev.flexmodel.system.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 系统表单布局节点，允许渲染语义组件。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record SystemFormItemRecord(
  String type,
  String name,
  String label,
  String tip,
  String width,
  Boolean hidden,
  Boolean readonly,
  String formName,
  String referenceField
) {
}
