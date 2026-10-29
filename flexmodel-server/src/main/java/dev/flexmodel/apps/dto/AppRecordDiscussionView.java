package dev.flexmodel.apps.dto;

import java.util.List;

/**
 * 记录讨论视图。
 *
 * @param id          讨论ID
 * @param parentId    顶层讨论为空，回复为顶层讨论ID
 * @param replyToName 回复目标作者姓名
 * @param content     讨论内容
 * @param authorId    作者ID
 * @param authorName  作者姓名快照
 * @param createdAt   创建时间
 * @param canDelete   当前用户是否可删除
 * @param replyCount  回复数量（顶层讨论）
 * @param replies     回复列表
 */
public record AppRecordDiscussionView(
  String id,
  String parentId,
  String replyToName,
  String content,
  String authorId,
  String authorName,
  String createdAt,
  boolean canDelete,
  int replyCount,
  List<AppRecordDiscussionView> replies
) {
}
