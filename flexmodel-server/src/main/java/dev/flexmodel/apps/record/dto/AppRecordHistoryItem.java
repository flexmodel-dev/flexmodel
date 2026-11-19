package dev.flexmodel.apps.record.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 记录历史列表项。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AppRecordHistoryItem(
  String id,
  String eventType,
  String source,
  String targetType,
  String targetFormName,
  String actorType,
  String actorId,
  String actorName,
  String occurredAt,
  String recordLabel,
  String summary,
  int changeCount,
  List<AppRecordHistoryChange> changesPreview,
  boolean hasMoreChanges
) {
}
