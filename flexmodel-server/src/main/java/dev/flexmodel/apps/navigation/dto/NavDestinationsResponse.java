package dev.flexmodel.apps.navigation.dto;

import java.util.List;

/**
 * 应用导航可选目的地响应。
 *
 * @param builtIn 内置模块
 * @param forms   应用表单
 */
public record NavDestinationsResponse(
  List<NavDestinationResponse> builtIn,
  List<NavDestinationResponse> forms
) {
}
