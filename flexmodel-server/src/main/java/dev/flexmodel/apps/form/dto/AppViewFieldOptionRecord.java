package dev.flexmodel.apps.form.dto;

/**
 * 视图能力目录中的表单字段说明。
 *
 * @param name    字段名
 * @param label   字段名称
 * @param type    字段类型
 * @param builtin 是否内置字段
 */
public record AppViewFieldOptionRecord(
  String name,
  String label,
  String type,
  boolean builtin
) {
}
