package dev.flexmodel.apps.dto;

import java.util.List;

/**
 * 应用表单局部更新请求。
 *
 * @param viewTabs 视图页签配置
 */
public record AppFormPatchRequest(
  List<AppFormViewTabRecord> viewTabs
) {
}
