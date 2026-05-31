package dev.flexmodel.project.dto;

import lombok.Getter;
import lombok.Setter;
import dev.flexmodel.codegen.entity.Branch;
import dev.flexmodel.codegen.entity.Project;

import java.util.List;

/**
 * @author cjbi
 */
@Setter
@Getter
public class ProjectResponse extends Project {

  private ProjectStats stats;
  private List<Branch> branches;

  public static ProjectResponse fromProject(Project project) {
    ProjectResponse response = new ProjectResponse();
    response.setId(project.getId());
    response.setName(project.getName());
    response.setDescription(project.getDescription());
    response.setEnabled(project.getEnabled());
    response.setOwnerId(project.getOwnerId());
    response.setCreatedBy(project.getCreatedBy());
    response.setUpdatedBy(project.getUpdatedBy());
    response.setCreatedAt(project.getCreatedAt());
    response.setCurrentBranch(project.getCurrentBranch());
    return response;
  }

  public record ProjectStats(Integer apiCount, Integer flowCount, Integer storageCount) {
  }

}
