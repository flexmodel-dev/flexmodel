package dev.flexmodel.apps.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 创建工作台请求。
 *
 * @param name        工作台名称
 * @param description 工作台描述
 * @param layout      工作台布局
 */
public record WorkbenchCreateRequest(
  @NotBlank(message = "工作台名称不能为空")
  String name,
  String description,
  WorkbenchLayoutRecord layout
) {
}
