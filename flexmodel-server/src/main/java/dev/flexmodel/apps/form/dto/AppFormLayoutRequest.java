package dev.flexmodel.apps.form.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

/**
 * 保存表单设计布局请求。
 *
 * @param items    有序布局节点
 * @param controls 按字段名配置的隐藏、必填、只读控制
 */
public record AppFormLayoutRequest(
  @NotNull List<AppFormItemRecord> items,
  Map<String, AppFormFieldControls> controls
) {
  public AppFormLayoutRequest(List<AppFormItemRecord> items) {
    this(items, null);
  }
}
