package dev.flexmodel.functions;

import dev.flexmodel.functions.dto.FunctionRuntimeDeployRequest;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.annotation.RegisterClientHeaders;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * Reactive REST client interface for the flexmodel-functions-runtime (Deno process).
 *
 * <p>Invoke endpoint sends the request body directly as JSON (no wrapping DTO).
 * authToken is passed via custom header.
 *
 * @author cjbi
 */
@RegisterRestClient(configKey = "function-runtime")
@RegisterClientHeaders(FunctionRuntimeClientHeadersFactory.class)
@Path("/functions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface FunctionRuntimeClient {

    @POST
    @Path("/deploy")
    Response deploy(FunctionRuntimeDeployRequest request);

    @POST
    @Path("/{projectId}/{name}/invoke")
    @Consumes(MediaType.WILDCARD)
    Response invoke(@PathParam("projectId") String projectId,
                    @PathParam("name") String name,
                    @HeaderParam("x-flexmodel-auth-token") String authToken,
                    Object body);

    @DELETE
    @Path("/{projectId}/{name}")
    Response delete(@PathParam("projectId") String projectId,
                    @PathParam("name") String name);
}
