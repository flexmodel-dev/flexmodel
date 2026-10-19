package dev.flexmodel.system.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 系统表单布局配置。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SystemFormLayoutRecord(
  List<SystemFormItemRecord> items
) {
}
