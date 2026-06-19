package dev.flexmodel.functions;

import dev.flexmodel.common.dto.PageDTO;
import dev.flexmodel.functions.dto.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

/**
 * REST API for Cloud Function management.
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

    @POST
    @Path("/{name}/deploy")
    public FunctionResponse deploy(@PathParam("projectId") String projectId,
                                    @PathParam("name") String name,
                                    @Valid FunctionDeployRequest request) {
        return functionService.deploy(projectId, name, request);
    }
}
