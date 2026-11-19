package dev.flexmodel.apps.form.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * 表单设计布局。
 *
 * @param items    有序布局节点
 * @param controls 按字段名配置的隐藏、必填、只读控制
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AppFormLayoutRecord(
  List<AppFormItemRecord> items,
  Map<String, AppFormFieldControls> controls
) {
  public AppFormLayoutRecord(List<AppFormItemRecord> items) {
    this(items, null);
  }
}
