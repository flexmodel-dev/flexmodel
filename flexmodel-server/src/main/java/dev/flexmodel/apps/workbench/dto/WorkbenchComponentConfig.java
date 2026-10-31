package dev.flexmodel.apps.workbench.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * 工作台组件配置。type 字段是配置判别符。
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = WorkbenchMetricConfig.class, name = "metric"),
  @JsonSubTypes.Type(value = WorkbenchChartConfig.class, name = "chart"),
  @JsonSubTypes.Type(value = WorkbenchDataLoaderConfig.class, name = "dataloader"),
  @JsonSubTypes.Type(value = WorkbenchShortcutsConfig.class, name = "shortcuts"),
  @JsonSubTypes.Type(value = WorkbenchTasksConfig.class, name = "tasks")
})
public sealed interface WorkbenchComponentConfig permits
  WorkbenchMetricConfig,
  WorkbenchChartConfig,
  WorkbenchDataLoaderConfig,
  WorkbenchShortcutsConfig,
  WorkbenchTasksConfig {
}
