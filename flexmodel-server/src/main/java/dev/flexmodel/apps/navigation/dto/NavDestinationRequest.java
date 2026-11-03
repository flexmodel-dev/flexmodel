package dev.flexmodel.apps.navigation.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 应用导航目的地请求。
 *
 * @param type        目的地类型
 * @param id          目的地ID
 * @param workbenchId 关联的具体工作台ID，仅 type=BUILTIN 且 id=workbench 时可选；缺省表示默认第一个工作台
 */
public record NavDestinationRequest(
  @NotBlank(message = "关联类型不能为空")
  String type,
  @NotBlank(message = "关联项不能为空")
  String id,
  String workbenchId
) {
  public NavDestinationRequest(String type, String id) {
    this(type, id, null);
  }
}
