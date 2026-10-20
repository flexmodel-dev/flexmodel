package dev.flexmodel.apps;

import dev.flexmodel.apps.dto.WorkbenchCreateRequest;
import dev.flexmodel.apps.dto.WorkbenchComponentDataResponse;
import dev.flexmodel.apps.dto.WorkbenchCapabilityResponse;
import dev.flexmodel.apps.dto.WorkbenchPatchRequest;
import dev.flexmodel.apps.dto.WorkbenchResponse;
import dev.flexmodel.apps.dto.WorkbenchSummaryResponse;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Tag(name = "应用", description = "应用工作台管理")
@Path("/apps/{appId}/workbenches")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class WorkbenchResource {

  @Inject
  WorkbenchService workbenchService;

  @Inject
  WorkbenchDataService workbenchDataService;

  @Operation(summary = "获取应用工作台列表")
  @GET
  public List<WorkbenchSummaryResponse> list(@PathParam("appId") String appId) {
    return workbenchService.findWorkbenches(appId);
  }

  @Operation(summary = "创建应用工作台")
  @POST
  public Response create(
    @PathParam("appId") String appId,
    @Valid WorkbenchCreateRequest request
  ) {
    WorkbenchResponse response = workbenchService.createWorkbench(appId, request);
    return Response.status(Response.Status.CREATED).entity(response).build();
  }

  @Operation(summary = "获取应用工作台能力目录", description = "返回 AI 生成工作台布局所需 schema、默认值、枚举值与可引用业务对象")
  @GET
  @Path("/capabilities")
  public WorkbenchCapabilityResponse capabilities(@PathParam("appId") String appId) {
    return workbenchService.findCapabilities(appId);
  }

  @Parameter(name = "workbenchId", description = "工作台ID", in = ParameterIn.PATH, required = true)
  @Operation(summary = "获取应用工作台详情")
  @GET
  @Path("/{workbenchId}")
  public WorkbenchResponse get(
    @PathParam("appId") String appId,
    @PathParam("workbenchId") String workbenchId
  ) {
    return workbenchService.findWorkbench(appId, workbenchId);
  }

  @Parameter(name = "workbenchId", description = "工作台ID", in = ParameterIn.PATH, required = true)
  @Operation(summary = "获取工作台组件真实数据")
  @GET
  @Path("/{workbenchId}/components/{componentId}/data")
  public WorkbenchComponentDataResponse componentData(
    @PathParam("appId") String appId,
    @PathParam("workbenchId") String workbenchId,
    @PathParam("componentId") String componentId,
    @QueryParam("page") @DefaultValue("1") Integer page,
    @QueryParam("size") @DefaultValue("20") Integer size,
    @QueryParam("search") String search
  ) {
    return workbenchDataService.findComponentData(appId, workbenchId, componentId, page, size, search);
  }

  @Parameter(name = "workbenchId", description = "工作台ID", in = ParameterIn.PATH, required = true)
  @Operation(summary = "局部更新应用工作台")
  @PATCH
  @Path("/{workbenchId}")
  public WorkbenchResponse patch(
    @PathParam("appId") String appId,
    @PathParam("workbenchId") String workbenchId,
    @Valid WorkbenchPatchRequest request
  ) {
    return workbenchService.patchWorkbench(appId, workbenchId, request);
  }

  @Parameter(name = "workbenchId", description = "工作台ID", in = ParameterIn.PATH, required = true)
  @Operation(summary = "删除应用工作台")
  @DELETE
  @Path("/{workbenchId}")
  public Response delete(
    @PathParam("appId") String appId,
    @PathParam("workbenchId") String workbenchId
  ) {
    workbenchService.deleteWorkbench(appId, workbenchId);
    return Response.noContent().build();
  }
}
