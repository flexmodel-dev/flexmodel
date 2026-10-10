package dev.flexmodel.apps.dto;

/**
 * 应用菜单关联项响应。
 *
 * @param type  关联类型，应用表单对外为 FORM
 * @param id    关联ID
 * @param label 展示名称
 */
public record AppMenuTargetResponse(
  String type,
  String id,
  String label
) {
}
