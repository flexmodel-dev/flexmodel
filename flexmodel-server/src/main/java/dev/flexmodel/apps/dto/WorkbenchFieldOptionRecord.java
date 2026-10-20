package dev.flexmodel.apps.dto;

/**
 * 工作台可引用字段说明。
 */
public record WorkbenchFieldOptionRecord(
  String name,
  String label,
  String type,
  Boolean measure
) {
}
