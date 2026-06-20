package dev.flexmodel.project;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import dev.flexmodel.project.dto.ProjectListRequest;
import dev.flexmodel.project.dto.ProjectResponse;
import dev.flexmodel.codegen.entity.Project;

import java.util.List;

/**
 * @author cjbi
 */
@Tag(name = "项目", description = "项目管理")
@Path("/projects")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProjectResource {

  @Inject
  ProjectService projectService;

  @APIResponse(
    name = "200",
    responseCode = "200",
    description = "OK",
    content = {
      @Content(
        mediaType = "application/json",
        schema = @Schema(
          type = SchemaType.ARRAY,
          implementation = ProjectResponse.class
        )
      )
    }
  )
  @Operation(summary = "获取项目列表")
  @GET
  public List<ProjectResponse> findProjects(@QueryParam("include") String include) {
    return projectService.findProjects(new ProjectListRequest(include));
  }

  @APIResponse(
    name = "200",
    responseCode = "200",
    description = "OK",
    content = {
      @Content(
        mediaType = "application/json",
        schema = @Schema(
          implementation = ProjectResponse.class
        )
      )
    }
  )
  @Operation(summary = "获取项目详情")
  @GET
  @Path("/{projectId}")
  public ProjectResponse findProject(@PathParam("projectId") String projectId) {
    return projectService.findProjectResponse(projectId);
  }

  @APIResponse(
    name = "200",
    responseCode = "200",
    description = "OK",
    content = {
      @Content(
        mediaType = "application/json",
        schema = @Schema(
          implementation = Project.class
        )
      )
    }
  )
  @Operation(summary = "创建项目")
  @POST
  public Project createProject(Project project) {
    return projectService.createProject(project);
  }

  @APIResponse(
    name = "200",
    responseCode = "200",
    description = "OK",
    content = {
      @Content(
        mediaType = "application/json",
        schema = @Schema(
          implementation = Project.class
        )
      )
    }
  )
  @Operation(summary = "更新项目")
  @PUT
  @Path("/{projectId}")
  public Project updateProject(
    @Parameter(name = "projectId", in = ParameterIn.PATH, description = "项目ID", required = true)
    @PathParam("projectId") String projectId,
    Project project) {
    project.setId(projectId);
    return projectService.updateProject(project);
  }

  @APIResponse(
    name = "200",
    responseCode = "200",
    description = "OK",
    content = {
      @Content(
        mediaType = "application/json",
        schema = @Schema(
          implementation = Project.class
        )
      )
    }
  )
  @Operation(summary = "部分更新项目")
  @PATCH
  @Path("/{projectId}")
  public Project patchProject(
    @Parameter(name = "projectId", in = ParameterIn.PATH, description = "项目ID", required = true)
    @PathParam("projectId") String projectId,
    Project project) {
    Project existingProject = projectService.findProject(projectId);
    if (project.getName() != null) {
      existingProject.setName(project.getName());
    }
    if (project.getDescription() != null) {
      existingProject.setDescription(project.getDescription());
    }
    return projectService.updateProject(existingProject);
  }

  @APIResponse(
    name = "200",
    responseCode = "200",
    description = "OK"
  )
  @Operation(summary = "删除项目")
  @DELETE
  @Path("/{projectId}")
  public void deleteProject(
    @Parameter(name = "projectId", in = ParameterIn.PATH, description = "项目ID", required = true)
    @PathParam("projectId") String projectId) {
    projectService.deleteProject(projectId);
  }

}
