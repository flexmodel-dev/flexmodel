package dev.flexmodel.apps.dto;

import java.util.List;

/**
 * 工作台布局。
 *
 * @param components 组件定义
 * @param items      布局项
 */
public record WorkbenchLayoutRecord(
  List<WorkbenchComponentRecord> components,
  List<WorkbenchLayoutItemRecord> items
) {

  public static WorkbenchLayoutRecord empty() {
    return new WorkbenchLayoutRecord(List.of(), List.of());
  }
}
