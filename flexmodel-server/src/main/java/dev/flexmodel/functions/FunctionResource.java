package dev.flexmodel.functions;

import dev.flexmodel.codegen.entity.Trigger;
import dev.flexmodel.common.dto.PageDTO;
import dev.flexmodel.functions.dto.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

/**
 * REST API for cloud function management.
 *
 * @author cjbi
 */
@ApplicationScoped
@Tag(name = "云函数", description = "云函数管理 (Flexmodel Functions)")
@Path("/projects/{projectId}/functions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class FunctionResource {

    @Inject
    FunctionService functionService;

    // ============================================================
    // Function CRUD
    // ============================================================

    @Operation(summary = "创建云函数")
    @POST
    public FunctionResponse create(@PathParam("projectId") String projectId,
                                    FunctionCreateRequest request) {
        return functionService.create(projectId, request);
    }

    @Operation(summary = "获取云函数列表")
    @GET
    public PageDTO<FunctionResponse> list(@PathParam("projectId") String projectId,
                                           @QueryParam("name") String name,
                                           @QueryParam("status") String status,
                                           @QueryParam("page") @DefaultValue("1") int page,
                                           @QueryParam("size") @DefaultValue("20") int size) {
        FunctionPageRequest request = new FunctionPageRequest();
        request.setName(name);
        request.setStatus(status);
        request.setPage(page);
        request.setSize(size);
        return functionService.findPage(projectId, request);
    }

    @Operation(summary = "获取云函数详情")
    @GET
    @Path("/{slug}")
    public FunctionResponse get(@PathParam("projectId") String projectId,
                                 @PathParam("slug") String slug) {
        return functionService.findById(projectId, slug);
    }

    @Operation(summary = "更新云函数")
    @PUT
    @Path("/{slug}")
    public FunctionResponse update(@PathParam("projectId") String projectId,
                                    @PathParam("slug") String slug,
                                    FunctionUpdateRequest request) {
        return functionService.update(projectId, slug, request);
    }

    @Operation(summary = "删除云函数")
    @DELETE
    @Path("/{slug}")
    public Response delete(@PathParam("projectId") String projectId,
                           @PathParam("slug") String slug) {
        functionService.delete(projectId, slug);
        return Response.ok().build();
    }

    @Operation(summary = "回滚到指定版本")
    @POST
    @Path("/{slug}/rollback")
    public FunctionResponse rollback(@PathParam("projectId") String projectId,
                                      @PathParam("slug") String slug,
                                      @QueryParam("version") @DefaultValue("1") int version) {
        return functionService.rollback(projectId, slug, version);
    }

    @Operation(summary = "获取版本列表")
    @GET
    @Path("/{slug}/versions")
    public List<FunctionVersionResponse> listVersions(@PathParam("projectId") String projectId,
                                                       @PathParam("slug") String slug) {
        return functionService.listVersions(projectId, slug);
    }

    // ============================================================
    // Invocation (internal and public)
    // ============================================================

    @Operation(summary = "调用云函数 (内部)")
    @POST
    @Path("/{slug}/invoke")
    public FunctionInvokeResponse invoke(@PathParam("projectId") String projectId,
                                          @PathParam("slug") String slug,
                                          FunctionInvokeRequest request,
                                          @HeaderParam(HttpHeaders.AUTHORIZATION) String authHeader) {
        return functionService.invoke(projectId, slug, request, authHeader);
    }

    // ============================================================
    // Trigger Management
    // ============================================================

    @Operation(summary = "获取函数触发器列表")
    @GET
    @Path("/{slug}/triggers")
    public List<Trigger> listTriggers(@PathParam("projectId") String projectId,
                                       @PathParam("slug") String slug) {
        return functionService.listTriggers(projectId, slug);
    }

    @Operation(summary = "添加触发器")
    @POST
    @Path("/{slug}/triggers")
    public Trigger addTrigger(@PathParam("projectId") String projectId,
                               @PathParam("slug") String slug,
                               Trigger trigger) {
        return functionService.addTrigger(projectId, slug, trigger);
    }

    @Operation(summary = "更新触发器")
    @PUT
    @Path("/{slug}/triggers/{triggerId}")
    public Trigger updateTrigger(@PathParam("projectId") String projectId,
                                  @PathParam("slug") String slug,
                                  @PathParam("triggerId") String triggerId,
                                  Trigger trigger) {
        return functionService.updateTrigger(projectId, slug, triggerId, trigger);
    }

    @Operation(summary = "删除触发器")
    @DELETE
    @Path("/{slug}/triggers/{triggerId}")
    public Response deleteTrigger(@PathParam("projectId") String projectId,
                                  @PathParam("slug") String slug,
                                  @PathParam("triggerId") String triggerId) {
        functionService.deleteTrigger(projectId, slug, triggerId);
        return Response.ok().build();
    }

    // ============================================================
    // Public HTTP Trigger Entrypoint
    // ============================================================

    @Operation(summary = "公开 HTTP 触发器入口")
    @POST
    @Path("/functions/{slug}")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN, MediaType.WILDCARD})
    public FunctionInvokeResponse publicInvoke(@PathParam("slug") String slug,
                                                FunctionInvokeRequest request,
                                                @HeaderParam(HttpHeaders.AUTHORIZATION) String authHeader,
                                                @HeaderParam("X-Project-Id") @DefaultValue("dev_test") String projectId) {
        return functionService.invoke(projectId, slug, request, authHeader);
    }
}
