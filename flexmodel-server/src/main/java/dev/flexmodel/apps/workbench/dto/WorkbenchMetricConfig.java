package dev.flexmodel.apps.workbench.dto;

/**
 * 指标组件配置。
 */
public record WorkbenchMetricConfig(
  String formName,
  String aggregation,
  String field
) implements WorkbenchComponentConfig {
}
