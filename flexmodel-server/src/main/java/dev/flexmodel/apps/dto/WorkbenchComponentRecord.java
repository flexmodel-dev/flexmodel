package dev.flexmodel.apps.dto;

/**
 * 工作台组件定义。
 *
 * @param id     组件ID
 * @param title  组件标题
 * @param type   组件类型
 * @param config 组件配置
 */
public record WorkbenchComponentRecord(
  String id,
  String title,
  String type,
  WorkbenchComponentConfig config
) {
}
