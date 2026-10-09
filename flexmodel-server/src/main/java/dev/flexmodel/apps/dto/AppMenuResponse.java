package dev.flexmodel.apps.dto;

import java.util.List;

/**
 * 应用菜单响应。
 *
 * @param id        菜单ID
 * @param type      菜单类型
 * @param name      菜单名称
 * @param iconName  图标名称
 * @param parentId  父级菜单ID
 * @param target    关联项
 * @param pageRoute 页面路由
 * @param children  子菜单
 */
public record AppMenuResponse(
  String id,
  String type,
  String name,
  String iconName,
  String parentId,
  AppMenuTargetResponse target,
  String pageRoute,
  List<AppMenuResponse> children
) {
}
