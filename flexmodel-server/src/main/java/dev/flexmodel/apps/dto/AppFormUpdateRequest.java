package dev.flexmodel.apps.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 更新应用表单请求。
 *
 * @param label       表单展示名
 * @param description 表单描述
 * @param displayField 主显字段
 */
public record AppFormUpdateRequest(
  @NotBlank(message = "表单名称不能为空")
  @Size(max = 100, message = "表单名称长度不能超过100个字符")
  String label,
  @Size(max = 500, message = "表单描述长度不能超过500个字符")
  String description,
  @JsonAlias("nameField") String displayField
) {
}
