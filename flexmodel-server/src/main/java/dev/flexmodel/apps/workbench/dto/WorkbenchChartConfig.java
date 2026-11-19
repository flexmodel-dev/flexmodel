package dev.flexmodel.apps.workbench.dto;

/**
 * 图表组件配置，chartType 支持 line/bar/pie。
 */
public record WorkbenchChartConfig(
  String chartType,
  String formName,
  String dimensionField,
  String measureField,
  String aggregation,
  Boolean showLegend,
  Boolean showLabel,
  Boolean smooth,
  String palette
) implements WorkbenchComponentConfig {
}
