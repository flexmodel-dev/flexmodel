package dev.flexmodel.apps.navigation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 创建或更新应用导航请求。
 *
 * @param type        导航类型
 * @param name        导航名称
 * @param iconName    图标名称
 * @param parentId    父级导航ID
 * @param destination 目的地
 */
public record AppNavSaveRequest(
  @NotBlank(message = "菜单类型不能为空")
  String type,
  @NotBlank(message = "菜单名称不能为空")
  @Size(max = 50, message = "菜单名称长度不能超过50个字符")
  String name,
  String iconName,
  String parentId,
  NavDestinationRequest destination,
  List<NavVisibilityRule> visibility
) {
}
