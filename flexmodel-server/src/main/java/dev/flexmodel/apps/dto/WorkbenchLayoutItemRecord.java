package dev.flexmodel.apps.dto;

/**
 * 工作台布局项。
 *
 * @param componentId 组件ID
 * @param x           横向网格位置
 * @param y           纵向网格位置
 * @param w           网格宽度
 * @param h           网格高度
 */
public record WorkbenchLayoutItemRecord(
  String componentId,
  Integer x,
  Integer y,
  Integer w,
  Integer h
) {
}
