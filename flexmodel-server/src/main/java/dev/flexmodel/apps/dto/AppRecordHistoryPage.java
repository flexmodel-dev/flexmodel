package dev.flexmodel.apps.dto;

import java.util.List;

/**
 * 记录历史分页结果。supported=false 表示当前表单或数据源不支持历史。
 */
public record AppRecordHistoryPage(
  boolean supported,
  List<AppRecordHistoryItem> list,
  String nextCursor,
  boolean hasMore
) {

  public static AppRecordHistoryPage unsupported() {
    return new AppRecordHistoryPage(false, List.of(), null, false);
  }
}
