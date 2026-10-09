package dev.flexmodel.apps.dto;

import java.util.List;

/**
 * 应用菜单在项目 metadata 中的存储结构。
 *
 * @param id       菜单ID
 * @param type     菜单类型
 * @param name     菜单名称
 * @param iconName 图标名称
 * @param parentId 父级菜单ID
 * @param target   关联项
 * @param children 子菜单
 */
public record AppMenuRecord(
  String id,
  String type,
  String name,
  String iconName,
  String parentId,
  AppMenuTargetRecord target,
  List<AppMenuRecord> children
) {
}
