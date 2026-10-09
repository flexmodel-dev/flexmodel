package dev.flexmodel.apps.dto;

/**
 * 应用菜单关联项存储结构。
 *
 * @param type 关联类型
 * @param id   关联ID
 */
public record AppMenuTargetRecord(
  String type,
  String id
) {
}
