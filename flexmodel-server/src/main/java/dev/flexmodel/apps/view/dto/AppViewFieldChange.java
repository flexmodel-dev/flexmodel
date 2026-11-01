package dev.flexmodel.apps.view.dto;

/**
 * 拖拽字段原值与目标值。
 *
 * @param from 原值
 * @param to   目标值
 */
public record AppViewFieldChange(Object from, Object to) {
}
