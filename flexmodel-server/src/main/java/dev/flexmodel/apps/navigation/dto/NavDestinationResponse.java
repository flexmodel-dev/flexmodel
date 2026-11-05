package dev.flexmodel.apps.navigation.dto;

/**
 * 应用导航目的地响应。
 *
 * @param type        目的地类型，应用表单对外为 FORM
 * @param id          目的地ID
 * @param label       展示名称
 * @param workbenchId 关联的具体工作台ID，仅 type=BUILTIN 且 id=workbench 时返回；null 表示默认第一个工作台
 */
public record NavDestinationResponse(
  String type,
  String id,
  String label,
  String workbenchId,
  String robotId
) {
  public NavDestinationResponse(String type, String id, String label, String workbenchId) {
    this(type, id, label, workbenchId, null);
  }
  public NavDestinationResponse(String type, String id, String label) {
    this(type, id, label, null, null);
  }
}
