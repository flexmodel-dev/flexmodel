package dev.flexmodel.apps.form.dto;

import java.util.List;
import java.util.Map;

/**
 * 应用表单视图配置。
 *
 * @param id            视图标识，创建表单时默认生成 id 为 "all" 的“全部”视图，可删除
 * @param label         视图名称
 * @param type          视图类型：TABLE、KANBAN、CALENDAR、GALLERY、DETAIL、HIERARCHY、GANTT
 * @param filter        共享筛选条件，支持 _and 或 _or，null 表示不筛选
 * @param sort          默认排序
 * @param displayFields 摘要显示字段，空表示使用表单布局字段
 * @param config        视图专属配置
 */
public record AppFormViewTabRecord(
  String id,
  String label,
  String type,
  Map<String, Object> filter,
  AppViewSort sort,
  List<String> displayFields,
  AppViewConfig config
) {
  public AppFormViewTabRecord(String id, String label, Map<String, Object> filter) {
    this(id, label, "TABLE", filter, null, List.of(), null);
  }
}
