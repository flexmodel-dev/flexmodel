package dev.flexmodel.apps.dto;

/**
 * 表单设计中的子表单配置。
 *
 * @param formName       子表单名称
 * @param referenceField 子表单中指向主表单的引用字段
 */
public record AppFormSubformRecord(
  String formName,
  String referenceField
) {
}
