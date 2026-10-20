package dev.flexmodel.apps.dto;

import java.util.List;

/**
 * 工作台组件配置。
 *
 * @param formName       数据表单
 * @param dimensionField 分组字段
 * @param measureField   指标字段
 * @param aggregation    聚合方式
 * @param showLegend     是否显示图例
 * @param showLabel      是否显示数值标签
 * @param smooth         是否平滑曲线
 * @param palette        配色
 * @param metric         指标标识
 * @param visibleColumns 明细列
 * @param shortcuts      快捷入口菜单
 * @param columnsPerRow  快捷入口每行数量
 */
public record WorkbenchComponentConfig(
  String formName,
  String dimensionField,
  String measureField,
  String aggregation,
  Boolean showLegend,
  Boolean showLabel,
  Boolean smooth,
  String palette,
  String metric,
  List<String> visibleColumns,
  List<String> shortcuts,
  Integer columnsPerRow
) {
}
