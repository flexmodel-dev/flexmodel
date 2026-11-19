package dev.flexmodel.apps.form.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 更新表单字段请求。
 *
 * @param label         字段展示名
 * @param required      是否必填
 * @param options       选项
 * @param referenceForm 引用表单
 */
public record AppFormFieldUpdateRequest(
  @NotBlank(message = "字段名称不能为空")
  @Size(max = 100, message = "字段名称长度不能超过100个字符")
  String label,
  boolean required,
  List<AppFormFieldOptionResponse> options,
  String referenceForm,
  AppFieldConfig config
) {
  public AppFormFieldUpdateRequest(String label, boolean required,
                                   List<AppFormFieldOptionResponse> options, String referenceForm) {
    this(label, required, options, referenceForm, null);
  }
}
