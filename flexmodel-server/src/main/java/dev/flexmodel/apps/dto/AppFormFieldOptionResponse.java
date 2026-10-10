package dev.flexmodel.apps.dto;

/**
 * 表单字段选项。
 *
 * @param id              选项ID
 * @param text            展示文本
 * @param color           颜色
 * @param defaultSelected 是否默认选中
 */
public record AppFormFieldOptionResponse(
  String id,
  String text,
  String color,
  Boolean defaultSelected
) {
}
