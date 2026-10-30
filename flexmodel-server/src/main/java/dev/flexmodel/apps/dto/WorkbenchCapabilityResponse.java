package dev.flexmodel.apps.dto;

import java.util.List;
import java.util.Map;

/**
 * AI 可用的工作台能力目录。
 */
public record WorkbenchCapabilityResponse(
  Integer schemaVersion,
  List<WorkbenchComponentSpecRecord> components,
  List<WorkbenchFormOptionRecord> forms,
  List<WorkbenchNavOptionRecord> shortcutNavs,
  Map<String, List<String>> availableValues,
  WorkbenchLayoutRecord exampleLayout
) {
}
