package dev.flexmodel.apps.dto;

/**
 * 应用导航目的地响应。
 *
 * @param type  目的地类型，应用表单对外为 FORM
 * @param id    目的地ID
 * @param label 展示名称
 */
public record NavDestinationResponse(
  String type,
  String id,
  String label
) {
}
