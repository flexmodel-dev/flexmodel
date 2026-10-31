package dev.flexmodel.apps.form.dto;

import java.util.Map;

/**
 * 应用表单视图页签配置。
 *
 * @param id     页签标识
 * @param label  页签名称
 * @param filter 原生筛选条件
 */
public record AppFormViewTabRecord(
  String id,
  String label,
  Map<String, Object> filter
) {
}
