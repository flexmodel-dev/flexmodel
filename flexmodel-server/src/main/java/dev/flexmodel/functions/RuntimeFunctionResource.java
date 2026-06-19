package dev.flexmodel.functions;

import dev.flexmodel.functions.dto.FunctionInvokeRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Runtime API for Cloud Function invocation.
 * <p>
 * The function's return value becomes the HTTP response body directly.
 * Execution metadata (execution time, logs) is available via the {@code X-Function-Meta} response header.
 *
 * @author cjbi
 */
@ApplicationScoped
@Path("/runtime/projects/{projectId}/functions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RuntimeFunctionResource {

    @Inject
    FunctionService functionService;

    @POST
    @Path("/{name}")
    public Response invoke(@PathParam("projectId") String projectId,
                           @PathParam("name") String name,
                           FunctionInvokeRequest request) {
        Response sidecarResponse = functionService.invoke(projectId, name, request);

        // Pass through function result directly as HTTP response
        Response.ResponseBuilder builder = Response
                .status(sidecarResponse.getStatus())
                .entity(sidecarResponse.readEntity(Object.class));

        // Forward x-function-meta header for observability
        String meta = sidecarResponse.getHeaderString("x-function-meta");
        if (meta != null) {
            builder.header("X-Function-Meta", meta);
        }

        return builder.build();
    }
}
