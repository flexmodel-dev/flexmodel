package dev.flexmodel.apps;

import dev.flexmodel.apps.dto.AppMenuOrderRequest;
import dev.flexmodel.apps.dto.AppMenuResponse;
import dev.flexmodel.apps.dto.AppMenuMember;
import dev.flexmodel.apps.dto.AppMenuSaveRequest;
import dev.flexmodel.apps.dto.AppMenuTargetsResponse;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

/**
 * 应用菜单 REST API。
 *
 * @author cjbi
 */
@Tag(name = "应用", description = "应用菜单管理")
@Path("/apps/{appId}/menus")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AppMenuResource {

  @Inject
  AppMenuService appMenuService;

  @Operation(summary = "获取应用菜单")
  @GET
  public List<AppMenuResponse> list(@PathParam("appId") String appId) {
    return appMenuService.findMenus(appId);
  }

  @Operation(summary = "获取应用菜单可选关联项")
  @GET
  @Path("/targets")
  public AppMenuTargetsResponse targets(@PathParam("appId") String appId) {
    return appMenuService.findTargets(appId);
  }

  @Operation(summary = "创建应用菜单")
  @POST
  public Response create(@PathParam("appId") String appId, @Valid AppMenuSaveRequest request) {
    AppMenuResponse response = appMenuService.createMenu(appId, request);
    return Response.status(Response.Status.CREATED).entity(response).build();
  }

  @Parameter(name = "menuId", description = "菜单ID", in = ParameterIn.PATH, required = true)
  @Operation(summary = "更新应用菜单")
  @PUT
  @Path("/{menuId}")
  public AppMenuResponse update(
    @PathParam("appId") String appId,
    @PathParam("menuId") String menuId,
    @Valid AppMenuSaveRequest request
  ) {
    return appMenuService.updateMenu(appId, menuId, request);
  }

  @Parameter(name = "menuId", description = "菜单ID", in = ParameterIn.PATH, required = true)
  @Operation(summary = "删除应用菜单")
  @DELETE
  @Path("/{menuId}")
  public Response delete(@PathParam("appId") String appId, @PathParam("menuId") String menuId) {
    appMenuService.deleteMenu(appId, menuId);
    return Response.noContent().build();
  }

  @Operation(summary = "调整应用菜单顺序")
  @PUT
  @Path("/order")
  public List<AppMenuResponse> reorder(
    @PathParam("appId") String appId,
    @Valid AppMenuOrderRequest request
  ) {
    return appMenuService.reorderMenus(appId, request);
  }

  @Operation(summary = "设置菜单授权成员")
  @PUT
  @Path("/{menuId}/members")
  public AppMenuResponse updateMembers(@PathParam("appId") String appId, @PathParam("menuId") String menuId,
                                       List<AppMenuMember> members) {
    return appMenuService.updateMembers(appId, menuId, members);
  }
}
