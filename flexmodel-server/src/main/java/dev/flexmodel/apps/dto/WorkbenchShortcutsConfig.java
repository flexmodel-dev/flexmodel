package dev.flexmodel.apps.dto;

import java.util.List;

/**
 * 快捷入口组件配置。
 */
public record WorkbenchShortcutsConfig(
  List<String> shortcuts,
  Integer columnsPerRow
) implements WorkbenchComponentConfig {
}
