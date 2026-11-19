package dev.flexmodel.apps.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 应用排序请求。
 *
 * @param ids 应用ID顺序，须包含且仅包含全部应用
 */
public record AppOrderRequest(
  @NotEmpty(message = "应用排序不能为空")
  List<String> ids
) {
}
