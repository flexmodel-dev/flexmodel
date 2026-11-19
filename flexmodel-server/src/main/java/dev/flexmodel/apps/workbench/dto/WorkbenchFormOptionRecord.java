package dev.flexmodel.apps.workbench.dto;

import java.util.List;

/**
 * 工作台可引用表单说明。
 */
public record WorkbenchFormOptionRecord(
  String name,
  String label,
  String displayField,
  List<WorkbenchFieldOptionRecord> fields
) {
}
