package dev.flexmodel.apps.dto;

import java.util.List;
import java.util.Map;

/**
 * 工作台组件类型说明。
 */
public record WorkbenchComponentSpecRecord(
  String type,
  String chartType,
  String label,
  String description,
  Integer defaultWidth,
  Integer defaultHeight,
  List<String> requiredFields,
  Map<String, Object> defaultConfig,
  String dataEndpoint
) {
}
