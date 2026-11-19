package dev.flexmodel.system.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.flexmodel.apps.form.dto.AppFieldConfig;
import dev.flexmodel.apps.form.dto.AppFormFieldOptionResponse;

import java.util.List;

/**
 * 系统表单字段配置。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SystemFormFieldRecord(
  String name,
  String label,
  String type,
  boolean required,
  boolean locked,
  boolean extension,
  boolean virtual,
  boolean readonly,
  List<AppFormFieldOptionResponse> options,
  String referenceForm,
  AppFieldConfig config
) {
  public SystemFormFieldRecord(String name, String label, String type, boolean required, boolean locked,
                                boolean extension, boolean virtual, boolean readonly,
                                List<AppFormFieldOptionResponse> options, String referenceForm) {
    this(name, label, type, required, locked, extension, virtual, readonly, options, referenceForm, null);
  }
}
