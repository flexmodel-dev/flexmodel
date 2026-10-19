package dev.flexmodel.system.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 系统表单页面配置。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SystemFormPageRecord(
  String title,
  String description,
  Integer pageSize,
  String leftComponent
) {
}
