package dev.flexmodel.functions;

import dev.flexmodel.functions.dto.FunctionInvokeRequest;
import dev.flexmodel.functions.dto.FunctionRuntimeDeployRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * Reactive REST client interface for the flexmodel-functions-runtime (Deno process).
 *
 * @author cjbi
 */
@RegisterRestClient(configKey = "function-runtime")
@Path("/functions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface FunctionRuntimeClient {

    @POST
    @Path("/deploy")
    Response deploy(FunctionRuntimeDeployRequest request);

    @POST
    @Path("/{projectId}/{name}/invoke")
    Response invoke(@PathParam("projectId") String projectId,
                    @PathParam("name") String name,
                    FunctionInvokeRequest request);

    @DELETE
    @Path("/{projectId}/{name}")
    Response delete(@PathParam("projectId") String projectId,
                    @PathParam("name") String name);
}
