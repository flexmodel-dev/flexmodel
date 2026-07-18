package dev.flexmodel.project;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

/**
 * 项目删除事件载荷。
 * <p>
 * 由 {@link ProjectService#deleteProject(String)} 在删除项目（含每个分支项目）后通过
 * Vert.x EventBus（地址 {@code "project.deleted"}）发布，供跨特性的 consumer 消费，
 * 用于清理与该项目关联但独立持久化的资源（如 Quartz 调度作业）。
 *
 * @author cjbi
 */
@Getter
@AllArgsConstructor
@ToString
public class ProjectDeletedEvent {
  private String projectId;
}
