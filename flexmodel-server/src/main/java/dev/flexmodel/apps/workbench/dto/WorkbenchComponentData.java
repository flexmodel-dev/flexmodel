package dev.flexmodel.apps.workbench.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = WorkbenchMetricData.class, name = "metric"),
  @JsonSubTypes.Type(value = WorkbenchChartData.class, name = "chart"),
  @JsonSubTypes.Type(value = WorkbenchDataLoaderData.class, name = "dataloader")
})
public sealed interface WorkbenchComponentData permits
  WorkbenchMetricData,
  WorkbenchChartData,
  WorkbenchDataLoaderData {
}
