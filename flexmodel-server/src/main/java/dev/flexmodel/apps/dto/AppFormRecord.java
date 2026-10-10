package dev.flexmodel.apps.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 应用表单在项目 metadata 中的存储结构。
 *
 * @param entityName  底层实体名，仅服务端内部使用
 * @param name        表单英文名称，对外暴露
 * @param label       表单展示名
 * @param description 表单描述
 * @param nameField   主显字段
 * @param searchField 搜索字段
 * @param createdAt   创建时间
 * @param updatedAt   更新时间
 * @param fields      字段配置
 */
public record AppFormRecord(
  String entityName,
  String name,
  String label,
  String description,
  String nameField,
  String searchField,
  LocalDateTime createdAt,
  LocalDateTime updatedAt,
  List<AppFormFieldRecord> fields
) {
}
