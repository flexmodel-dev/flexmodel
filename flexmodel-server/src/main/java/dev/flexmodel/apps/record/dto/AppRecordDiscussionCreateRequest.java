package dev.flexmodel.apps.record.dto;

import java.util.List;

/**
 * 创建记录讨论请求。
 *
 * @param content          讨论内容
 * @param parentId         回复的顶层讨论ID；顶层讨论为空
 * @param replyToId        回复目标讨论ID，用于展示“谁回复谁”并通知目标作者
 * @param mentionedUserIds 被提及的用户ID列表
 */
public record AppRecordDiscussionCreateRequest(
  String content,
  String parentId,
  String replyToId,
  List<String> mentionedUserIds
) {
}
