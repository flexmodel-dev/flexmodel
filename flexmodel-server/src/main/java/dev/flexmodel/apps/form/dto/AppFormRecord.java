package dev.flexmodel.apps.form.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 应用表单在项目 metadata 中的存储结构。
 *
 * @param name          表单英文名称，同时作为实体名
 * @param label         表单展示名
 * @param description   表单描述
 * @param displayField  主显字段
 * @param searchField   搜索字段
 * @param createdAt     创建时间
 * @param updatedAt     更新时间
 * @param fields        字段配置
 * @param layout        表单布局
 * @param viewTabs      视图配置
 */
public record AppFormRecord(
  String name,
  String label,
  String description,
  @JsonAlias("nameField") String displayField,
  String searchField,
  LocalDateTime createdAt,
  LocalDateTime updatedAt,
  List<AppFormFieldRecord> fields,
  AppFormLayoutRecord layout,
  List<AppFormViewTabRecord> viewTabs
) {
}
