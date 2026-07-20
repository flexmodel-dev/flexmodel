package dev.flexmodel.scheduling;

import dev.flexmodel.codegen.entity.Trigger;
import dev.flexmodel.common.authz.RequiresPermissions;
import dev.flexmodel.common.dto.PageDTO;
import dev.flexmodel.scheduling.dto.TriggerDTO;
import dev.flexmodel.scheduling.dto.TriggerPageRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * @author cjbi
 */
@ApplicationScoped
@Tag(name = "触发器", description = "触发器管理")
@Path("/projects/{projectId}/triggers")
public class TriggerResource {
  @Inject
  TriggerService triggerService;

  @Operation(summary = "获取单个触发器")
  @GET
  @RequiresPermissions("scheduling:view")
  @Path("/{id}")
  public TriggerDTO findById(@PathParam("projectId") String projectId,
                             @PathParam("id") String id) {
    return triggerService.findById(projectId, id);
  }

  @Operation(summary = "获取触发器列表")
  @GET
  @RequiresPermissions("scheduling:view")
  public PageDTO<TriggerDTO> findPage(@PathParam("projectId") String projectId,
                                      @QueryParam("name") String name,
                                      @QueryParam("jobType") String jobType,
                                      @QueryParam("jobId") String jobId,
                                      @QueryParam("jobGroup") String jobGroup,
                                      @QueryParam("page") @DefaultValue("1") Integer page,
                                      @QueryParam("size") @DefaultValue("15") Integer size) {
    TriggerPageRequest request = new TriggerPageRequest();
    request.setProjectId(projectId);
    request.setName(name);
    request.setJobType(jobType);
    request.setJobId(jobId);
    request.setJobGroup(jobGroup);
    request.setPage(page);
    request.setSize(size);
    return triggerService.findPage(projectId, request);
  }

  @Operation(summary = "创建触发器")
  @POST
  @RequiresPermissions("scheduling:execute")
  public Trigger create(@PathParam("projectId") String projectId, Trigger trigger) {
    return triggerService.create(projectId, trigger);
  }

  @Operation(summary = "更新触发器")
  @PUT
  @RequiresPermissions("scheduling:execute")
  @Path("/{id}")
  public Trigger update(@PathParam("projectId") String projectId, @PathParam("id") String id, Trigger req) {
    req.setId(id);
    return triggerService.update(projectId, req);
  }

  @Operation(summary = "部分更新触发器")
  @PATCH
  @RequiresPermissions("scheduling:execute")
  @Path("/{id}")
  public Trigger patch(@PathParam("projectId") String projectId, @PathParam("id") String id, Trigger req) {
    TriggerDTO dto = triggerService.findById(projectId, id);
    if (req.getState() != null) {
      dto.setState(req.getState());
    }
    return triggerService.update(projectId, dto);
  }

  @Operation(summary = "删除触发器")
  @DELETE
  @RequiresPermissions("scheduling:execute")
  @Path("/{id}")
  public void deleteById(@PathParam("projectId") String projectId, @PathParam("id") String id) {
    triggerService.deleteById(projectId, id);
  }

  @Operation(summary = "立即执行触发器")
  @POST
  @RequiresPermissions("scheduling:execute")
  @Path("/{id}/execute")
  public Trigger executeNow(@PathParam("projectId") String projectId, @PathParam("id") String id) {
    return triggerService.executeNow(projectId, id);
  }

}
