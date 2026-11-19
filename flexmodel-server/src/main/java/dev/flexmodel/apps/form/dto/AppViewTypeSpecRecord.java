package dev.flexmodel.apps.form.dto;

import java.util.List;

/**
 * 视图类型说明。
 *
 * @param type   视图类型：TABLE、KANBAN、CALENDAR、GALLERY、DETAIL、HIERARCHY、GANTT
 * @param label  视图类型名称
 * @param config 该类型 config 字段的契约清单，空表示无需 config
 */
public record AppViewTypeSpecRecord(
  String type,
  String label,
  List<AppViewConfigSpecRecord> config
) {
}
