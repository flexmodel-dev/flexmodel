package dev.flexmodel.apps.workbench.dto;

import java.util.List;

/**
 * 明细组件配置。
 */
public record WorkbenchDataLoaderConfig(
  String formName,
  List<String> visibleColumns
) implements WorkbenchComponentConfig {
}
