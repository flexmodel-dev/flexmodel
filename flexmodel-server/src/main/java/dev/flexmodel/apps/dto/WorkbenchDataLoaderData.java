package dev.flexmodel.apps.dto;

import java.util.List;
import java.util.Map;

public record WorkbenchDataLoaderData(
  List<Map<String, Object>> list,
  long total
) implements WorkbenchComponentData {
}
