package dev.flexmodel.system.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 系统表单列表列配置。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SystemFormColumnRecord(
  String name,
  String label,
  String width,
  Boolean sortable,
  Boolean hidden
) {
}
