package dev.flexmodel.apps.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 更新应用请求。
 *
 * @param name         应用名称
 * @param description  应用描述
 * @param iconName     应用图标
 * @param primaryColor 应用主色
 */
public record AppUpdateRequest(
  @NotBlank(message = "应用名称不能为空")
  @Size(max = 100, message = "应用名称长度不能超过100个字符")
  String name,
  @Size(max = 500, message = "应用描述长度不能超过500个字符")
  String description,
  @Pattern(regexp = "ShoppingCart|Banknote|Boxes|Folder|Tags|TrendingUp", message = "应用图标不合法")
  String iconName,
  @Pattern(regexp = "indigo|blue|green|amber|rose|black", message = "应用主色不合法")
  String primaryColor
) {
}
