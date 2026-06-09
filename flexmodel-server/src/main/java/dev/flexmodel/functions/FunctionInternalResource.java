package dev.flexmodel.functions;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Internal API endpoints for the Deno sidecar.
 * These endpoints are called by the sidecar for lazy-loading source code.
 *
 * @author cjbi
 */
@ApplicationScoped
@Path("/internal/functions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class FunctionInternalResource {

    @Inject
    FunctionService functionService;

    /**
     * Get source code for a specific version.
     * Called by the Deno sidecar when lazy-loading function source code.
     */
    @GET
    @Path("/{functionId}/versions/{version}/source")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getSourceCode(@PathParam("functionId") String functionId,
                                   @PathParam("version") int version) {
        try {
            String sourceCode = functionService.getSourceCode(functionId, version);
            return Response.ok(sourceCode).build();
        } catch (FunctionException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(e.getMessage())
                .build();
        }
    }
}
