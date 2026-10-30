package dev.flexmodel.apps.dto;

import java.util.List;

/**
 * 应用导航响应。
 *
 * @param id          导航ID
 * @param type        导航类型
 * @param name        导航名称
 * @param iconName    图标名称
 * @param parentId    父级导航ID
 * @param destination 目的地
 * @param pageRoute   页面路由
 * @param children    子导航
 * @param visibility  可见性规则
 * @param ownerId     应用所有者ID
 */
public record AppNavResponse(
  String id,
  String type,
  String name,
  String iconName,
  String parentId,
  NavDestinationResponse destination,
  String pageRoute,
  List<AppNavResponse> children,
  List<NavVisibilityRule> visibility,
  String ownerId
) {
}
