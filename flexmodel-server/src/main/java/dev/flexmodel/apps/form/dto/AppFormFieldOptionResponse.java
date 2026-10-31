package dev.flexmodel.apps.form.dto;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * 表单字段选项。
 *
 * @param id              选项ID
 * @param text            展示文本
 * @param color           颜色
 * @param defaultSelected 是否默认选中
 */
public record AppFormFieldOptionResponse(
  String id,
  String text,
  String color,
  Boolean defaultSelected
) {

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static AppFormFieldOptionResponse fromText(String text) {
    return new AppFormFieldOptionResponse(text, text, null, false);
  }
}
