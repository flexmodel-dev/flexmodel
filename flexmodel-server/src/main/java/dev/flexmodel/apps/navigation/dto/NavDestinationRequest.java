package dev.flexmodel.apps.navigation.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 应用导航目的地请求。
 *
 * @param type 目的地类型
 * @param id   目的地ID
 */
public record NavDestinationRequest(
  @NotBlank(message = "关联类型不能为空")
  String type,
  @NotBlank(message = "关联项不能为空")
  String id
) {
}
