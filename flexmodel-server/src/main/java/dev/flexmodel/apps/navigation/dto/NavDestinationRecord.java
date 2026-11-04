package dev.flexmodel.apps.navigation.dto;

/**
 * 导航目的地存储结构。
 *
 * @param type        目的地类型
 * @param id          目的地ID
 * @param workbenchId 关联的具体工作台ID，仅 type=BUILTIN 且 id=workbench 时使用；null 表示默认第一个工作台
 */
public record NavDestinationRecord(String type, String id, String workbenchId, String robotId) {
  public NavDestinationRecord(String type, String id, String workbenchId) {
    this(type, id, workbenchId, null);
  }
  public NavDestinationRecord(String type, String id) {
    this(type, id, null, null);
  }
}
