package dev.flexmodel.project;

import dev.flexmodel.project.dto.BranchCreateRequest;
import dev.flexmodel.codegen.entity.Branch;
import dev.flexmodel.codegen.entity.Project;
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

import java.util.List;

/**
 * 分支管理 REST 资源
 *
 * @author cjbi
 */
@Tag(name = "分支", description = "项目分支管理")
@Path("/projects/{projectId}/branches")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BranchResource {

  @Inject
  BranchService branchService;

  @APIResponse(
    name = "200",
    responseCode = "200",
    description = "OK",
    content = {
      @Content(
        mediaType = "application/json",
        schema = @Schema(
          type = SchemaType.ARRAY,
          implementation = Branch.class
        )
      )
    }
  )
  @Operation(summary = "获取项目分支列表")
  @GET
  public List<Branch> listBranches(
    @Parameter(name = "projectId", in = ParameterIn.PATH, description = "项目ID", required = true)
    @PathParam("projectId") String projectId) {
    return branchService.listBranches(projectId);
  }

  @APIResponse(
    name = "200",
    responseCode = "200",
    description = "OK",
    content = {
      @Content(
        mediaType = "application/json",
        schema = @Schema(
          implementation = Branch.class
        )
      )
    }
  )
  @Operation(summary = "创建分支")
  @POST
  public Branch createBranch(
    @Parameter(name = "projectId", in = ParameterIn.PATH, description = "项目ID", required = true)
    @PathParam("projectId") String projectId,
    BranchCreateRequest request) {
    return branchService.createBranch(
      projectId,
      request.getName(),
      request.getSourceBranch(),
      request.getDescription()
    );
  }

  @APIResponse(
    name = "200",
    responseCode = "200",
    description = "OK"
  )
  @Operation(summary = "删除分支")
  @DELETE
  @Path("/{branch}")
  public void deleteBranch(
    @Parameter(name = "projectId", in = ParameterIn.PATH, description = "项目ID", required = true)
    @PathParam("projectId") String projectId,
    @Parameter(name = "branch", in = ParameterIn.PATH, description = "分支名称", required = true)
    @PathParam("branch") String branch) {
    branchService.deleteBranch(projectId, branch);
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
  @Operation(summary = "切换到指定分支")
  @PUT
  @Path("/{branch}/switch")
  public Project switchBranch(
    @Parameter(name = "projectId", in = ParameterIn.PATH, description = "项目ID", required = true)
    @PathParam("projectId") String projectId,
    @Parameter(name = "branch", in = ParameterIn.PATH, description = "分支名称", required = true)
    @PathParam("branch") String branch) {
    return branchService.switchBranch(projectId, branch);
  }
}
