package dev.flexmodel.apps.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.Map;

/**
 * 应用菜单排序请求。
 *
 * @param root     根级菜单ID顺序
 * @param children 分组ID到子菜单ID顺序的映射
 */
public record AppMenuOrderRequest(
  @NotEmpty(message = "菜单顺序不能为空")
  List<String> root,
  Map<String, List<String>> children
) {
}
