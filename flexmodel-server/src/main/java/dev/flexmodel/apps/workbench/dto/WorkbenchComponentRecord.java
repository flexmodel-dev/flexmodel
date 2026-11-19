package dev.flexmodel.apps.workbench.dto;

/**
 * 工作台组件定义。
 *
 * @param id        组件ID
 * @param title     组件标题
 * @param placement 网格位置
 * @param config    组件配置，config.type 是组件类型
 */
public record WorkbenchComponentRecord(
  String id,
  String title,
  WorkbenchPlacementRecord placement,
  WorkbenchComponentConfig config
) {
}
