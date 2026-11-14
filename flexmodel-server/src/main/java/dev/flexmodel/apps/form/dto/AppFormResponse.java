package dev.flexmodel.apps.form.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 应用表单响应。
 *
 * @param name         表单英文名称
 * @param label        表单展示名
 * @param description  表单描述
 * @param displayField 主显字段
 * @param searchField  搜索字段
 * @param managed      是否已纳入应用表单配置
 * @param builtin      是否内置表单
 * @param createdAt    创建时间
 * @param updatedAt    更新时间
 * @param fields       字段列表
 * @param layout       表单布局
 * @param viewTabs     视图配置
 */
public record AppFormResponse(
  String name,
  String label,
  String description,
  String displayField,
  String searchField,
  boolean managed,
  boolean builtin,
  LocalDateTime createdAt,
  LocalDateTime updatedAt,
  List<AppFormFieldResponse> fields,
  AppFormLayoutRecord layout,
  List<AppFormViewTabRecord> viewTabs
) {
}
