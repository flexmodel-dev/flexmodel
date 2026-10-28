package dev.flexmodel.apps.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 创建或更新应用菜单请求。
 *
 * @param type     菜单类型
 * @param name     菜单名称
 * @param iconName 图标名称
 * @param parentId 父级菜单ID
 * @param target   关联项
 */
public record AppMenuSaveRequest(
  @NotBlank(message = "菜单类型不能为空")
  String type,
  @NotBlank(message = "菜单名称不能为空")
  @Size(max = 50, message = "菜单名称长度不能超过50个字符")
  String name,
  String iconName,
  String parentId,
  AppMenuTargetRequest target,
  List<AppMenuMember> members
) {
}
