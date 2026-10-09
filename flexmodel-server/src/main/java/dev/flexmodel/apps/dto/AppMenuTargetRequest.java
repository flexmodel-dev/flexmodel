package dev.flexmodel.apps.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 应用菜单关联项请求。
 *
 * @param type 关联类型
 * @param id   关联ID
 */
public record AppMenuTargetRequest(
  @NotBlank(message = "关联类型不能为空")
  String type,
  @NotBlank(message = "关联项不能为空")
  String id
) {
}
