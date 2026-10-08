package dev.flexmodel.apps.dto;

import dev.flexmodel.codegen.entity.Project;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 应用响应。
 *
 * @param id           应用ID
 * @param projectId    项目ID，与应用ID一致
 * @param name         应用名称
 * @param description  应用描述
 * @param iconName     应用图标
 * @param primaryColor 应用主色
 * @param enabled      是否启用
 * @param createdAt    创建时间
 * @param updatedAt    更新时间
 */
public record AppResponse(
  String id,
  String projectId,
  String name,
  String description,
  String iconName,
  String primaryColor,
  Boolean enabled,
  LocalDateTime createdAt,
  LocalDateTime updatedAt
) {

  public static AppResponse from(Project project, String iconName, String primaryColor) {
    return new AppResponse(
      project.getId(),
      project.getId(),
      project.getName(),
      project.getDescription(),
      iconName,
      primaryColor,
      Objects.requireNonNullElse(project.getEnabled(), true),
      project.getCreatedAt(),
      project.getUpdatedAt());
  }
}
