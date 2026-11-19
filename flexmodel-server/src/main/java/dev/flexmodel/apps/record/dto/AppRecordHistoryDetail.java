package dev.flexmodel.apps.record.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * 记录历史事件详情。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AppRecordHistoryDetail(
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
  List<AppRecordHistoryChange> changes,
  Map<String, Object> context
) {
}
