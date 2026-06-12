package dev.flexmodel.project;

import dev.flexmodel.codegen.entity.Project;

import java.util.List;

/**
 * @author cjbi
 */
public interface ProjectRepository {

  List<Project> findProjects();

  /**
   * 返回顶级项目（parentProjectId 为 null）
   */
  List<Project> findTopLevelProjects();

  /**
   * 返回指定父项目的所有分支项目
   */
  List<Project> findBranchProjects(String parentProjectId);

  Project findProject(String projectId);

  Project save(Project project);

  void delete(String projectId);
}
