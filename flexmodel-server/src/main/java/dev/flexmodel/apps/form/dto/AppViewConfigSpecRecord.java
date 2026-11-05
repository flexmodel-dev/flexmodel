package dev.flexmodel.apps.form.dto;

import java.util.List;

/**
 * 视图 config 字段契约。
 *
 * @param name         config 字段名，例如 groupField
 * @param required     是否必填
 * @param fieldTypes   可引用的表单字段类型；null 表示该 config 项不引用表单字段
 * @param enumValues   允许的枚举值；null 表示非枚举项
 * @param defaultValue 缺省值
 * @param description  用途说明
 */
public record AppViewConfigSpecRecord(
  String name,
  boolean required,
  List<String> fieldTypes,
  List<String> enumValues,
  String defaultValue,
  String description
) {
}
