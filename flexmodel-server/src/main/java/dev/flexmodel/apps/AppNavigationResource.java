package dev.flexmodel.apps;

import dev.flexmodel.apps.dto.AppNavOrderRequest;
import dev.flexmodel.apps.dto.AppNavResponse;
import dev.flexmodel.apps.dto.AppNavSaveRequest;
import dev.flexmodel.apps.dto.NavDestinationsResponse;
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
import java.util.Map;

/**
 * 应用导航 REST API。
 *
 * @author cjbi
 */
@Tag(name = "应用", description = "应用导航管理")
@Path("/apps/{appId}/navigation")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AppNavigationResource {

  @Inject
  AppNavigationService appNavigationService;

  @Operation(summary = "获取应用导航")
  @GET
  public List<AppNavResponse> list(@PathParam("appId") String appId) {
    return appNavigationService.findNavigation(appId);
  }

  @Operation(summary = "获取应用导航可选目的地")
  @GET
  @Path("/destinations")
  public NavDestinationsResponse destinations(@PathParam("appId") String appId) {
    return appNavigationService.findDestinations(appId);
  }

  @Operation(summary = "创建应用导航")
  @POST
  public Response create(@PathParam("appId") String appId, @Valid AppNavSaveRequest request) {
    AppNavResponse response = appNavigationService.createNav(appId, request);
    return Response.status(Response.Status.CREATED).entity(response).build();
  }

  @Parameter(name = "navId", description = "导航ID", in = ParameterIn.PATH, required = true)
  @Operation(summary = "更新应用导航")
  @PUT
  @Path("/{navId}")
  public AppNavResponse update(
    @PathParam("appId") String appId,
    @PathParam("navId") String navId,
    @Valid AppNavSaveRequest request
  ) {
    return appNavigationService.updateNav(appId, navId, request);
  }

  @Parameter(name = "navId", description = "导航ID", in = ParameterIn.PATH, required = true)
  @Operation(summary = "删除应用导航")
  @DELETE
  @Path("/{navId}")
  public Response delete(@PathParam("appId") String appId, @PathParam("navId") String navId) {
    appNavigationService.deleteNav(appId, navId);
    return Response.noContent().build();
  }

  @Operation(summary = "调整应用导航顺序")
  @PUT
  @Path("/order")
  public List<AppNavResponse> reorder(
    @PathParam("appId") String appId,
    @Valid AppNavOrderRequest request
  ) {
    return appNavigationService.reorderNavigation(appId, request);
  }

  @Operation(summary = "局部更新应用导航")
  @PATCH
  @Path("/{navId}")
  public AppNavResponse patch(@PathParam("appId") String appId, @PathParam("navId") String navId,
                              Map<String, Object> patch) {
    return appNavigationService.patchNav(appId, navId, patch);
  }
}
