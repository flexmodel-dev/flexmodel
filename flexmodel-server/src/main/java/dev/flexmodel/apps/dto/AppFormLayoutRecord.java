package dev.flexmodel.apps.dto;

import java.util.List;
import java.util.Map;

/**
 * 表单设计布局。
 *
 * @param fields     布局字段顺序
 * @param widths     字段宽度
 * @param properties 字段展示属性
 * @param subforms   子表单配置
 * @param advanced   高级控制扩展数据
 */
public record AppFormLayoutRecord(
  List<String> fields,
  Map<String, Integer> widths,
  Map<String, AppFormItemPropertiesRecord> properties,
  Map<String, AppFormSubformRecord> subforms,
  Map<String, Object> advanced
) {
}