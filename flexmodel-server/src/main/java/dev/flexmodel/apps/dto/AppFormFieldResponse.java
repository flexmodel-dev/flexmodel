package dev.flexmodel.apps.dto;

import java.util.List;

/**
 * 表单字段响应。
 *
 * @param name          实体字段名
 * @param label         展示名称
 * @param type          表单字段类型
 * @param required      是否必填
 * @param builtin       是否内置字段
 * @param options       选项
 * @param referenceForm 引用表单
 */
public record AppFormFieldResponse(
  String name,
  String label,
  String type,
  boolean required,
  boolean builtin,
  List<AppFormFieldOptionResponse> options,
  String referenceForm
) {
}
