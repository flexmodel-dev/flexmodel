package dev.flexmodel.functions;

import dev.flexmodel.functions.dto.FunctionInvokeRequest;
import dev.flexmodel.functions.dto.SidecarDeployRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * Reactive REST client interface for the flexmodel-sidecar (Deno process).
 *
 * @author cjbi
 */
@RegisterRestClient(configKey = "sidecar")
@RegisterProvider(SidecarResponseExceptionMapper.class)
@Path("/functions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface SidecarClient {

    @POST
    @Path("/deploy")
    Response deploy(SidecarDeployRequest request);

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
