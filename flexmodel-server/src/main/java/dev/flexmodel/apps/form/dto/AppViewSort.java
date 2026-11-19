package dev.flexmodel.apps.form.dto;

/**
 * 视图默认排序配置。
 *
 * @param field 排序字段
 * @param order 排序方向，ASC 或 DESC
 */
public record AppViewSort(String field, String order) {
}
