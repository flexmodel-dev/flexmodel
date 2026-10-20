package dev.flexmodel.apps.dto;

import java.util.List;

public record WorkbenchChartData(
  List<WorkbenchDataPointRecord> rows
) implements WorkbenchComponentData {
}
