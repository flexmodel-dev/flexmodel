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
    @Path("/{name}")
    public FunctionResponse get(@PathParam("projectId") String projectId,
                                 @PathParam("name") String name) {
        return functionService.findByName(projectId, name);
    }

    @DELETE
    @Path("/{name}")
    public void delete(@PathParam("projectId") String projectId,
                       @PathParam("name") String name) {
        functionService.delete(projectId, name);
    }

    // ============================================================
    // Deploy
    // ============================================================

    /**
     * Deploy (upsert) a function: create if not exists, update if exists.
     */
    @POST
    @Path("/{name}/deploy")
    public FunctionResponse deploy(@PathParam("projectId") String projectId,
                                    @PathParam("name") String name,
                                    FunctionDeployRequest request) {
        return functionService.deploy(projectId, name, request);
    }

    /**
     * Re-push an existing function to the Deno sidecar without modifying DB data.
     * Useful when the sidecar was restarted and needs to recover function state.
     */
    @POST
    @Path("/{name}/redeploy")
    public FunctionResponse redeploy(@PathParam("projectId") String projectId,
                                      @PathParam("name") String name) {
        return functionService.redeploy(projectId, name);
    }

    // ============================================================
    // Invocation
    // ============================================================

    @POST
    @Path("/{name}/invoke")
    public FunctionInvokeResponse invoke(@PathParam("projectId") String projectId,
                                          @PathParam("name") String name,
                                          FunctionInvokeRequest request) {
        return functionService.invoke(projectId, name, request);
    }
}
