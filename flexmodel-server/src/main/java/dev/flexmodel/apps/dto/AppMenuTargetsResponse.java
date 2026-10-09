package dev.flexmodel.apps.dto;

import java.util.List;

/**
 * 应用菜单可选关联项响应。
 *
 * @param builtIn  内置模块
 * @param entities 实体
 */
public record AppMenuTargetsResponse(
  List<AppMenuTargetResponse> builtIn,
  List<AppMenuTargetResponse> entities
) {
}
