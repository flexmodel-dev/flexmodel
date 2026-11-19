package dev.flexmodel.system.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * 系统表单完整保存请求。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SystemFormSaveRequest(
  @NotBlank(message = "表单名称不能为空")
  @Size(max = 100, message = "表单名称长度不能超过100个字符")
  String label,
  @Size(max = 500, message = "表单描述长度不能超过500个字符")
  String description,
  @JsonAlias("nameField") String displayField,
  String searchField,
  List<SystemFormFieldRecord> fields,
  SystemFormLayoutRecord layout,
  List<SystemFormColumnRecord> listColumns,
  SystemFormPageRecord page,
  Map<String, SystemComponentRecord> components
) {
}
