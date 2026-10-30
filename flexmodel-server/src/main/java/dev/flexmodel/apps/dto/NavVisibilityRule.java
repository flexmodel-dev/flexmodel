package dev.flexmodel.apps.dto;

/**
 * 导航可见性规则：命中的用户、角色或组织可见。
 */
public record NavVisibilityRule(String type, String id, Boolean includeChildren) {
}
