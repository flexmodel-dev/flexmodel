package dev.flexmodel.project;

import dev.flexmodel.common.SessionContextHolder;
import dev.flexmodel.common.utils.StringUtils;
import dev.flexmodel.flow.service.FlowDeploymentService;
import dev.flexmodel.project.dto.ProjectListRequest;
import dev.flexmodel.project.dto.ProjectResponse;
import dev.flexmodel.storage.StorageService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import dev.flexmodel.codegen.entity.Project;

import java.util.List;
import java.util.Objects;

/**
 * @author cjbi
 */
@ApplicationScoped
public class ProjectService {

  @Inject
  ProjectRepository projectRepository;


  @Inject
  FlowDeploymentService flowDeploymentService;
  @Inject
  StorageService storageService;

  public List<Project> findProjects() {
    return projectRepository.findProjects();
  }

  public List<ProjectResponse> findProjects(ProjectListRequest request) {
    return projectRepository.findProjects().stream()
      .map(project -> {
          ProjectResponse response = ProjectResponse.fromProject(project);
          if (Objects.equals(request.getIncldue(), "stats")) {
            ProjectResponse.ProjectStats projectStats = new ProjectResponse.ProjectStats(
              -1,
              flowDeploymentService.count(project.getId()),
              -1,
              storageService.count(project.getId())
            );
            response.setStats(projectStats);
          }
          return response;
        }
      ).toList();
  }
  public Project findProject(String projectId) {
    return projectRepository.findProject(projectId);
  }

  public Project createProject(Project project) {
    if (!StringUtils.isBlank(project.getId()) && findProject(project.getId()) != null) {
      throw new IllegalArgumentException("项目ID已经存在");
    }
    if (StringUtils.isBlank(project.getId())) {
      project.setId(null);
    }
    project.setOwnerId(SessionContextHolder.getUserId());
    return projectRepository.save(project);
  }

  public Project updateProject(Project project) {
    if ("default".equals(project.getId())) {
      throw new IllegalArgumentException("默认项目不能修改");
    }
    Project existingProject = findProject(project.getId());
    if (existingProject == null) {
      throw new IllegalArgumentException("项目不存在");
    }
    project.setCreatedAt(existingProject.getCreatedAt());
    project.setCreatedBy(existingProject.getCreatedBy());
    project.setOwnerId(existingProject.getOwnerId());
    return projectRepository.save(project);
  }

  public void deleteProject(String projectId) {
    if ("default".equals(projectId)) {
      throw new IllegalArgumentException("默认项目不能删除");
    }
    projectRepository.delete(projectId);
  }
}
