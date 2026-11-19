package dev.flexmodel.apps.form.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 字段的隐藏、必填、只读控制配置，未配置的属性保持字段默认行为。
 *
 * @param hidden   隐藏控制
 * @param required 必填控制，字段默认必填始终生效
 * @param readonly 只读控制
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = false)
public record AppFormFieldControls(
  AppFormFieldControlRecord hidden,
  AppFormFieldControlRecord required,
  AppFormFieldControlRecord readonly
) {
}
