package dev.flexmodel.apps.workbench.dto;

import java.time.LocalDateTime;

/**
 * 工作台摘要响应。
 *
 * @param id          工作台ID
 * @param name        工作台名称
 * @param description 工作台描述
 * @param createdAt   创建时间
 * @param updatedAt   更新时间
 */
public record WorkbenchSummaryResponse(
  String id,
  String name,
  String description,
  LocalDateTime createdAt,
  LocalDateTime updatedAt
) {
}
