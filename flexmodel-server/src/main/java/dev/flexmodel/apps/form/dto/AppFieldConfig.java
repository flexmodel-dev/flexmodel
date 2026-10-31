package dev.flexmodel.apps.form.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/** 字段的应用层属性；存入表单 metadata，不改变引擎字段类型。null 表示使用类型默认行为。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AppFieldConfig {
  private Object defaultValue;
  private Boolean defaultNow;
  private Boolean creatable;
  private Boolean updatable;
  private List<String> commonValues;
  private Integer maxLength;
  private String pattern;
  private String validationMessage;
  private Boolean allowNegative;
  private Integer decimalPlaces;
  private Boolean thousandsSeparator;
  private String prefix;
  private String suffix;
  private String dateFormat;
  private String displayStyle;
}
