package dev.flexmodel.apps.dto;

/**
 * 应用表单引用字段的服务端渲染结果。
 */
public record AppReferenceValueResponse(
  String id,
  String displayValue,
  String referenceForm
) {
}
