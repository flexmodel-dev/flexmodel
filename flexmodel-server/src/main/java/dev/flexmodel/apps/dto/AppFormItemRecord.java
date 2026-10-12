package dev.flexmodel.apps.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 表单布局节点。
 *
 * @param type           节点类型
 * @param name           字段名
 * @param label          展示标题
 * @param tip            提示文案
 * @param width          宽度
 * @param hidden         是否隐藏
 * @param readonly       是否只读
 * @param formName       子表单名称
 * @param referenceField 子表单引用字段
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AppFormItemRecord(
  String type,
  String name,
  String label,
  String tip,
  String width,
  Boolean hidden,
  Boolean readonly,
  String formName,
  String referenceField
) {
}
