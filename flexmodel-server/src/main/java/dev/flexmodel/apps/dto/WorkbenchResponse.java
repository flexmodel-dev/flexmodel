package dev.flexmodel.apps.dto;

import java.time.LocalDateTime;

/**
 * 工作台详情响应。
 *
 * @param id          工作台ID
 * @param name        工作台名称
 * @param description 工作台描述
 * @param layout      工作台布局
 * @param createdAt   创建时间
 * @param updatedAt   更新时间
 */
public record WorkbenchResponse(
  String id,
  String name,
  String description,
  WorkbenchLayoutRecord layout,
  LocalDateTime createdAt,
  LocalDateTime updatedAt
) {
}
