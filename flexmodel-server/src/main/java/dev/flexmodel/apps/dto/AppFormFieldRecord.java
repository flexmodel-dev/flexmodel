package dev.flexmodel.apps.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.List;

/**
 * 应用表单字段在项目 metadata 中的存储结构。
 *
 * @param name          实体字段名
 * @param label         展示名称
 * @param type          表单字段类型
 * @param required      是否必填
 * @param builtin       是否内置字段
 * @param options       选项
 * @param referenceForm 引用表单
 */
public record AppFormFieldRecord(
  String name,
  String label,
  String type,
  boolean required,
  @JsonAlias("system") boolean builtin,
  List<AppFormFieldOptionResponse> options,
  String referenceForm
) {
}
