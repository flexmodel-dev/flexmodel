package dev.flexmodel.apps.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 创建表单字段请求。
 *
 * @param label         字段展示名
 * @param type          表单字段类型
 * @param required      是否必填
 * @param options       选项
 * @param referenceForm 引用表单
 */
public record AppFormFieldCreateRequest(
  @NotBlank(message = "字段名称不能为空")
  @Size(max = 100, message = "字段名称长度不能超过100个字符")
  String label,
  @NotBlank(message = "字段类型不能为空")
  String type,
  boolean required,
  List<AppFormFieldOptionResponse> options,
  String referenceForm
) {
}
