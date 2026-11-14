package dev.flexmodel.apps.record.dto;

import java.util.List;

/**
 * 记录讨论分页响应。
 *
 * @param list      顶层讨论列表，回复内嵌
 * @param total     全部讨论数量（含回复）
 * @param rootTotal 顶层讨论数量
 */
public record AppRecordDiscussionPage(
  List<AppRecordDiscussionView> list,
  long total,
  long rootTotal
) {
}
