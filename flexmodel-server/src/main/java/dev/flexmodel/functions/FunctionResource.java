package dev.flexmodel.functions;

import dev.flexmodel.common.dto.PageDTO;
import dev.flexmodel.functions.dto.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

/**
 * REST API for cloud function management.
 *
 * @author cjbi
 */
@ApplicationScoped
@Path("/projects/{projectId}/functions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class FunctionResource {

    @Inject
    FunctionService functionService;

    // ============================================================
    // Function Management
    // ============================================================

    @GET
    public PageDTO<FunctionResponse> list(@PathParam("projectId") String projectId,
                                           @QueryParam("name") String name,
                                           @QueryParam("page") @DefaultValue("1") int page,
                                           @QueryParam("size") @DefaultValue("20") int size) {
        FunctionPageRequest request = new FunctionPageRequest();
        request.setName(name);
        request.setPage(page);
        request.setSize(size);
        return functionService.findPage(projectId, request);
    }

    @GET
    @Path("/{slug}")
    public FunctionResponse get(@PathParam("projectId") String projectId,
                                 @PathParam("slug") String slug) {
        return functionService.findById(projectId, slug);
    }

    @DELETE
    @Path("/{slug}")
    public void delete(@PathParam("projectId") String projectId,
                       @PathParam("slug") String slug) {
        functionService.delete(projectId, slug);
    }

    // ============================================================
    // Deploy
    // ============================================================

    /**
     * Deploy (upsert) a function: create if not exists, update if exists.
     */
    @POST
    @Path("/{slug}/deploy")
    public FunctionResponse deploy(@PathParam("projectId") String projectId,
                                    @PathParam("slug") String slug,
                                    FunctionDeployRequest request) {
        return functionService.deploy(projectId, slug, request);
    }

    // ============================================================
    // Invocation
    // ============================================================

    @POST
    @Path("/{slug}/invoke")
    public FunctionInvokeResponse invoke(@PathParam("projectId") String projectId,
                                          @PathParam("slug") String slug,
                                          FunctionInvokeRequest request) {
        return functionService.invoke(projectId, slug, request);
    }
}
