package dev.flexmodel.apps.view.dto;

import java.util.Map;

/**
 * 视图拖拽写入请求。
 *
 * @param changes 字段变化集合，key 为字段名
 * @param mode    拖拽方式：MOVE、RESIZE_START、RESIZE_END
 */
public record AppViewMoveRequest(
  Map<String, AppViewFieldChange> changes,
  String mode
) {
}
