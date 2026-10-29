package dev.flexmodel.apps.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 记录历史单字段变化视图。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AppRecordHistoryChange(
  String fieldName,
  String fieldLabel,
  String fieldType,
  Object before,
  Object after,
  String beforeDisplay,
  String afterDisplay
) {
}
