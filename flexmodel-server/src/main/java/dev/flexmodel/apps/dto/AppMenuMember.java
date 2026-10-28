package dev.flexmodel.apps.dto;

/** 菜单授权的目录成员。 */
public record AppMenuMember(String type, String id, Boolean includeChildren) {
}
