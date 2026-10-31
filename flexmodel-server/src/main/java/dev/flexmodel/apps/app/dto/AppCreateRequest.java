package dev.flexmodel.apps.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 创建应用请求。
 *
 * @param name         应用名称
 * @param description  应用描述
 * @param projectId    项目ID，可选
 * @param iconName     应用图标
 * @param primaryColor 应用主色
 */
public record AppCreateRequest(
  @NotBlank(message = "应用名称不能为空")
  @Size(max = 100, message = "应用名称长度不能超过100个字符")
  String name,
  @Size(max = 500, message = "应用描述长度不能超过500个字符")
  String description,
  @Pattern(regexp = "^[a-z][a-z0-9_]{1,62}$", message = "项目ID格式不正确")
  String projectId,
  @Pattern(regexp = "^[A-Z][A-Za-z0-9]{0,63}$", message = "应用图标不合法")
  String iconName,
  @Pattern(regexp = "^[a-z][a-z0-9-]{0,31}$", message = "应用主色不合法")
  String primaryColor
) {
}
