package dev.flexmodel.apps.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 保存表单设计布局请求。
 *
 * @param items 有序布局节点
 */
public record AppFormLayoutRequest(
  @NotNull List<AppFormItemRecord> items
) {
}