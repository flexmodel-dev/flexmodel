package dev.flexmodel.system.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 保存在系统模型 metadata 中的表单配置。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SystemFormMetadataRecord(
  String label,
  String description,
  @JsonAlias("nameField") String displayField,
  String searchField,
  LocalDateTime createdAt,
  LocalDateTime updatedAt,
  List<SystemFormFieldRecord> fields,
  SystemFormLayoutRecord layout,
  List<SystemFormColumnRecord> listColumns,
  SystemFormPageRecord page,
  Map<String, SystemComponentRecord> components,
  boolean designable
) {
}
