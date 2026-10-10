package dev.flexmodel.apps.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 创建应用表单请求。
 *
 * @param label           表单展示名
 * @param name            表单英文名称
 * @param description     表单描述
 * @param createNameField 是否创建名称字段
 */
public record AppFormCreateRequest(
  @NotBlank(message = "表单名称不能为空")
  @Size(max = 100, message = "表单名称长度不能超过100个字符")
  String label,
  @NotBlank(message = "表单内部标识不能为空")
  @Size(max = 64, message = "表单英文名称长度不能超过64个字符")
  @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]{0,63}$", message = "表单英文名称只能以字母开头，并包含字母、数字或下划线")
  String name,
  @Size(max = 500, message = "表单描述长度不能超过500个字符")
  String description,
  boolean createNameField
) {
}
