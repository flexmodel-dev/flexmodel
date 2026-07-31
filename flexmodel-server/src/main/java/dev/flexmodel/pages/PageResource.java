package dev.flexmodel.pages;

import dev.flexmodel.common.authz.RequiresPermissions;
import dev.flexmodel.pages.dto.PageSiteResponse;
import dev.flexmodel.pages.dto.PageSiteUpdateRequest;
import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.io.InputStream;

/**
 * Pages 站点管理 REST API（每项目一个站点）。
 *
 * @author cjbi
 */
@Tag(name = "Pages", description = "静态站点托管")
@ApplicationScoped
@Path("/projects/{projectId}/page")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PageResource {

  @Inject
  PageService pageService;

  @Operation(summary = "获取 Pages 站点配置")
  @GET
  @RequiresPermissions("pages:view")
  public Response getPageSite(@PathParam("projectId") String projectId) {
    PageSiteResponse response = pageService.getPageSite(projectId);
    if (response == null) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    return Response.ok(response).build();
  }

  @Operation(summary = "更新 Pages 站点配置")
  @PUT
  @RequiresPermissions("pages:deploy")
  public PageSiteResponse updatePageSite(@PathParam("projectId") String projectId,
                                         PageSiteUpdateRequest request) {
    return pageService.updatePageSite(projectId, request);
  }

  @Operation(summary = "上传 zip 部署")
  @POST
  @Path("/deployments")
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  @RequiresPermissions("pages:deploy")
  public PageSiteResponse deploy(@PathParam("projectId") String projectId,
                                 @FormParam("file") InputStream file) {
    return pageService.deploy(projectId, file);
  }

  @Operation(summary = "切生产别名（回滚）")
  @PUT
  @Path("/production")
  @RequiresPermissions("pages:deploy")
  public PageSiteResponse setProduction(@PathParam("projectId") String projectId,
                                        @QueryParam("deploymentId") String deploymentId) {
    if (deploymentId == null || deploymentId.isBlank()) {
      throw new PageException("deploymentId is required");
    }
    return pageService.setProduction(projectId, deploymentId);
  }
}
