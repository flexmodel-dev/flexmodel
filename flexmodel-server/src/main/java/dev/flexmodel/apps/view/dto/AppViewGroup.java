package dev.flexmodel.apps.view.dto;

/**
 * 看板分组。
 *
 * @param value 分组原值，未分组为 null
 * @param label 分组展示文本
 * @param color 选项颜色
 * @param total 分组记录数
 * @param empty 是否为未分组
 */
public record AppViewGroup(
  Object value,
  String label,
  String color,
  long total,
  boolean empty
) {
}
