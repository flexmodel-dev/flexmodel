package dev.flexmodel.apps.form.dto;

import java.util.List;
import java.util.Map;

/**
 * AI 可用的表单视图页签能力目录。
 *
 * @param viewTypes         视图类型说明与 config 字段契约
 * @param filterOperators   当前表单各字段类型支持的筛选操作符
 * @param sortBuiltinFields 排序可用的内置字段
 * @param limits            视图数量与长度限制
 * @param fields            当前表单字段，用于选择 config 引用、筛选与显示字段
 * @param views             当前视图页签，整体替换时需要保留的清单
 * @param exampleViews      示例视图列表
 */
public record AppViewCapabilityResponse(
  List<AppViewTypeSpecRecord> viewTypes,
  Map<String, List<String>> filterOperators,
  List<String> sortBuiltinFields,
  Map<String, Integer> limits,
  List<AppViewFieldOptionRecord> fields,
  List<AppFormViewTabRecord> views,
  List<Map<String, Object>> exampleViews
) {
}
