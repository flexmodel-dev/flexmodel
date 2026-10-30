package dev.flexmodel.apps.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.Map;

/**
 * 应用导航排序请求。
 *
 * @param root     根级导航ID顺序
 * @param children 分组ID到子导航ID顺序的映射
 */
public record AppNavOrderRequest(
  @NotEmpty(message = "菜单顺序不能为空")
  List<String> root,
  Map<String, List<String>> children
) {
}
