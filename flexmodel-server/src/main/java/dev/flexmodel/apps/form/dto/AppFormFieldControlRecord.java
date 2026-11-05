package dev.flexmodel.apps.form.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * 单个字段控制：在指定场景、满足条件时生效。
 *
 * @param scenes    生效场景：CREATE / UPDATE
 * @param condition 记录查询 filter DSL 条件；为空表示在场景下始终生效
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = false)
public record AppFormFieldControlRecord(
  List<String> scenes,
  Map<String, Object> condition
) {
}
