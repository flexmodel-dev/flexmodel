package dev.flexmodel.apps.workbench.dto;

import java.util.List;

public record WorkbenchChartData(
  List<WorkbenchDataPointRecord> rows
) implements WorkbenchComponentData {
}
