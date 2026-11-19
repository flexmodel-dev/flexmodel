package dev.flexmodel.apps.view.dto;

import java.util.Map;

/**
 * 视图数据查询请求。
 *
 * @param page       页码
 * @param size       每页数量
 * @param search     关键词
 * @param filter     临时高级查询条件
 * @param sortField  临时排序字段
 * @param sortOrder  临时排序方向
 * @param scope      查询范围
 * @param groupValue 看板列分组值
 * @param groupEmpty 查询未分组列
 * @param rangeStart 日历、甘特窗口开始
 * @param rangeEnd   日历、甘特窗口结束
 * @param parentId   层级父节点
 */
public record AppViewQueryRequest(
  Integer page,
  Integer size,
  String search,
  Map<String, Object> filter,
  String sortField,
  String sortOrder,
  String scope,
  Object groupValue,
  Boolean groupEmpty,
  String rangeStart,
  String rangeEnd,
  String parentId
) {
}
