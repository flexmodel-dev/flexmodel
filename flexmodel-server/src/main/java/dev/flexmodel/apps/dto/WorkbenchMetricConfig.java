package dev.flexmodel.apps.dto;

/**
 * 指标组件配置。
 */
public record WorkbenchMetricConfig(
  String formName,
  String metric
) implements WorkbenchComponentConfig {
}
