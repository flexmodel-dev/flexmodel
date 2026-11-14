package dev.flexmodel.apps.navigation.dto;

import java.util.List;

/**
 * 应用导航在项目 metadata 中的存储结构。
 *
 * @param id          导航ID
 * @param type        导航类型
 * @param name        导航名称
 * @param iconName    图标名称
 * @param parentId    父级导航ID
 * @param destination 导航目的地
 * @param children    子导航
 * @param visibility  可见性规则
 */
public record AppNavRecord(
  String id,
  String type,
  String name,
  String iconName,
  String parentId,
  NavDestinationRecord destination,
  List<AppNavRecord> children,
  List<NavVisibilityRule> visibility
) {
}
