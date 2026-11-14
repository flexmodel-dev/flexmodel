package dev.flexmodel.system.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 系统表单语义组件配置。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SystemComponentRecord(
  String type,
  String label,
  Boolean hidden
) {
}
