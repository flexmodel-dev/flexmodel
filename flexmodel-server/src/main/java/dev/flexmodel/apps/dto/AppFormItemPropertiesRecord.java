package dev.flexmodel.apps.dto;

/**
 * 表单项展示属性。
 *
 * @param label    展示名称
 * @param tip      填写提示
 * @param required 是否必填
 * @param readonly 是否只读
 * @param hidden   是否隐藏
 */
public record AppFormItemPropertiesRecord(
  String label,
  String tip,
  Boolean required,
  Boolean readonly,
  Boolean hidden
) {
}