package dev.flexmodel.apps;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 历史事件数据载体。由业务代码在操作时复制完成，历史写入只消费该副本，
 * 不在提交后重新读取可能已经变化的原记录。
 */
public record AppRecordHistoryEvent(
  String appId,
  String eventKey,
  String operationId,
  String formName,
  String formLabel,
  String recordId,
  String recordLabel,
  String eventType,
  String source,
  String actorType,
  String actorId,
  String actorName,
  LocalDateTime occurredAt,
  List<Map<String, Object>> changes,
  Map<String, Object> context,
  List<Target> targets
) {

  public record Target(String formName, String recordId, String targetType) {
  }
}
