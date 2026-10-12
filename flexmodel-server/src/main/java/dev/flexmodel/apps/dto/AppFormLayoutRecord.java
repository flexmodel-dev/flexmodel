package dev.flexmodel.apps.dto;

import java.util.List;

/**
 * 表单设计布局。
 *
 * @param items 有序布局节点
 */
public record AppFormLayoutRecord(
  List<AppFormItemRecord> items
) {
}