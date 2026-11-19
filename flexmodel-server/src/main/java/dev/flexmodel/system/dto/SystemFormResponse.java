package dev.flexmodel.system.dto;

import dev.flexmodel.auth.dto.ResourceTreeResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 系统表单响应。
 */
public record SystemFormResponse(
  String name,
  String label,
  String description,
  String displayField,
  String searchField,
  LocalDateTime createdAt,
  LocalDateTime updatedAt,
  List<SystemFormFieldRecord> fields,
  SystemFormLayoutRecord layout,
  List<SystemFormColumnRecord> listColumns,
  SystemFormPageRecord page,
  Map<String, SystemComponentRecord> components,
  boolean designable,
  List<ResourceTreeResponse> resources
) {
}
