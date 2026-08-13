package dev.flexmodel.open;

import dev.flexmodel.functions.FunctionService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Open API — 边缘函数调用（Java 代理）。
 * <p>
 * 路径前缀 {@code /open/{projectId}/functions}，
 * 面向终端用户（IdP 用户 / open scope API Key）。
 * 仅暴露 invoke，不暴露 deploy/delete/list/get（admin only）。
 *
 * @author cjbi
 */
@ApplicationScoped
@Path("/open/{projectId}/functions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OpenFunctionResource {

  @Inject
  FunctionService functionService;

  /**
   * 代理调用边缘函数（Java → Deno runtime）。
   */
  @POST
  @Path("/{name}/invoke")
  public Response invoke(
    @PathParam("projectId") String projectId,
    @PathParam("name") String name,
    Object request
  ) {
    Response runtimeResponse = functionService.invoke(projectId, name, request);

    // Read body as raw bytes to bypass Jackson JSON parsing — edge functions
    // may return arbitrary content types (text, binary, malformed JSON).
    byte[] body = runtimeResponse.readEntity(byte[].class);
    Response.ResponseBuilder builder = Response
      .status(runtimeResponse.getStatus())
      .entity(body);
    String contentType = runtimeResponse.getHeaderString("Content-Type");
    if (contentType != null) {
      builder.header("Content-Type", contentType);
    }

    String meta = runtimeResponse.getHeaderString("x-function-meta");
    if (meta != null) {
      builder.header("X-Function-Meta", meta);
    }

    return builder.build();
  }
}
