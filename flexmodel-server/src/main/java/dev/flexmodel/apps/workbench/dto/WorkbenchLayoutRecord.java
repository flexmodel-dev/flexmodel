package dev.flexmodel.apps.workbench.dto;

import java.util.List;

/**
 * 工作台布局。
 *
 * @param schemaVersion 布局协议版本，当前为 1
 * @param components    组件定义，placement 内嵌在每个组件中
 */
public record WorkbenchLayoutRecord(
  Integer schemaVersion,
  List<WorkbenchComponentRecord> components
) {

  public static final int CURRENT_SCHEMA_VERSION = 1;

  public static WorkbenchLayoutRecord empty() {
    return new WorkbenchLayoutRecord(CURRENT_SCHEMA_VERSION, List.of());
  }
}
