package dev.flexmodel.apps.form.dto;

/**
 * 视图专属配置。字段按视图类型使用，其余字段为 null。
 *
 * @param groupField    看板分组字段
 * @param titleField    卡片、事件或任务标题字段
 * @param coverField    画廊封面字段
 * @param imageFit      画廊图片裁切方式：COVER 或 CONTAIN
 * @param startField    日历、甘特开始字段
 * @param endField      日历、甘特结束字段
 * @param calendarUnit  日历默认周期：MONTH、WEEK 或 DAY
 * @param detailMode    详情展示模式：LIST 或 SINGLE
 * @param parentField   层级父节点字段
 * @param hierarchyMode 层级展示形态：TREE 或 TABLE
 * @param ganttScale    甘特默认刻度：DAY、WEEK 或 MONTH
 */
public record AppViewConfig(
  String groupField,
  String titleField,
  String coverField,
  String imageFit,
  String startField,
  String endField,
  String calendarUnit,
  String detailMode,
  String parentField,
  String hierarchyMode,
  String ganttScale
) {
  public static AppViewConfig empty() {
    return new AppViewConfig(null, null, null, null, null, null, null, null, null, null, null);
  }
}
