package dev.flexmodel.apps.app;

import dev.flexmodel.apps.app.dto.AppCreateRequest;
import dev.flexmodel.apps.app.dto.AppResponse;
import dev.flexmodel.apps.app.dto.AppUpdateRequest;
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

/**
 * 应用管理 REST API。
 *
 * @author cjbi
 */
@Tag(name = "应用", description = "应用管理")
@Path("/apps")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AppResource {

  @Inject
  AppService appService;

  @Operation(summary = "获取应用列表")
  @GET
  public List<AppResponse> list() {
    return appService.findApps();
  }

  @Operation(summary = "获取应用详情")
  @GET
  @Path("/{appId}")
  public AppResponse get(@PathParam("appId") String appId) {
    return appService.findApp(appId);
  }

  @Operation(summary = "创建应用")
  @POST
  public Response create(@Valid AppCreateRequest request) {
    AppResponse response = appService.createApp(request);
    return Response.status(Response.Status.CREATED).entity(response).build();
  }

  @Parameter(name = "appId", description = "应用ID", in = ParameterIn.PATH, required = true)
  @Operation(summary = "更新应用")
  @PUT
  @Path("/{appId}")
  public AppResponse update(@PathParam("appId") String appId, @Valid AppUpdateRequest request) {
    return appService.updateApp(appId, request);
  }

  @Parameter(name = "appId", description = "应用ID", in = ParameterIn.PATH, required = true)
  @Operation(summary = "删除应用")
  @DELETE
  @Path("/{appId}")
  public Response delete(@PathParam("appId") String appId) {
    appService.deleteApp(appId);
    return Response.noContent().build();
  }
}
