package dev.flexmodel.apps.view.dto;

import java.util.List;
import java.util.Map;

/**
 * 视图数据查询结果。
 *
 * @param viewId         视图标识
 * @param type           视图类型
 * @param list           记录或树节点
 * @param total          记录总数或分组总数
 * @param groups         看板分组
 * @param childFlags     树节点是否存在下级
 * @param hasMore        是否还有更多数据
 * @param movable        当前视图是否允许拖拽
 * @param disabledReason 禁止拖拽的原因
 */
public record AppViewQueryResponse(
  String viewId,
  String type,
  List<Map<String, Object>> list,
  long total,
  List<AppViewGroup> groups,
  Map<String, Boolean> childFlags,
  boolean hasMore,
  boolean movable,
  String disabledReason
) {
}
