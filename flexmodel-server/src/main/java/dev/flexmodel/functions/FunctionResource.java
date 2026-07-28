package dev.flexmodel.functions;

import dev.flexmodel.common.authz.RequiresPermissions;
import dev.flexmodel.common.config.web.jwt.JwtService;
import dev.flexmodel.common.dto.PageDTO;
import dev.flexmodel.functions.dto.*;
import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * REST API for Edge Function management.
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

  @Inject
  JwtService jwtService;

    @GET
    @RequiresPermissions("function:view")
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
    @RequiresPermissions("function:view")
    public FunctionResponse get(@PathParam("projectId") String projectId,
                                 @PathParam("name") String name) {
        return functionService.findByName(projectId, name);
    }

    @DELETE
    @Path("/{name}")
    @RequiresPermissions("function:execute")
    public void delete(@PathParam("projectId") String projectId,
                       @PathParam("name") String name) {
        functionService.delete(projectId, name);
    }

    @POST
    @Path("/{name}/deploy")
    @RequiresPermissions("function:execute")
    public FunctionResponse deploy(@PathParam("projectId") String projectId,
                                    @PathParam("name") String name,
                                    @Valid FunctionDeployRequest request) {
        return functionService.deploy(projectId, name, request);
    }

  @POST
  @Path("/{name}/invoke")
  @RequiresPermissions("function:execute")
  public Response invoke(@PathParam("projectId") String projectId,
                         @PathParam("name") String name,
                         Object request) {
    Response runtimeResponse = functionService.invoke(projectId, name, request);

    // Pass through function result directly as HTTP response
    Response.ResponseBuilder builder = Response
      .status(runtimeResponse.getStatus())
      .entity(runtimeResponse.readEntity(Object.class));

    // Forward x-function-meta header for observability
    String meta = runtimeResponse.getHeaderString("x-function-meta");
    if (meta != null) {
      builder.header("X-Function-Meta", meta);
    }

    return builder.build();
  }

  /**
   * Sign an invoke-token for edge function direct invocation.
   *
   * <p>The frontend uses this token to directly call the Deno Runtime at the URL
   * defined by {@code flexmodel.edge-url-template}, bypassing the Java server.
   *
   * @param projectId project ID
   * @param name      function name
   * @return InvokeTokenResponse containing invoke-token and runtime URL
   */
  @POST
  @Path("/{name}/invoke-token")
  @RequiresPermissions("function:execute")
  public InvokeTokenResponse invokeToken(@PathParam("projectId") String projectId,
                                         @PathParam("name") String name) {
    return functionService.signInvokeToken(projectId, name);
  }

  /**
   * Get function source code for auto-deploy by the Deno runtime.
   *
   * <p>Only accessible with an internal token (account = "svc:runtime").
   * Used by the Deno runtime when a function is not registered (404) to
   * pull the source code and deploy on-demand.
   *
   * @param projectId project ID
   * @param name      function name
   * @return FunctionRuntimeDeployRequest containing source files and metadata
   */
  @GET
  @Path("/{name}/source")
  @PermitAll
  public FunctionRuntimeDeployRequest source(@PathParam("projectId") String projectId,
                                             @PathParam("name") String name,
                                             @HeaderParam("Authorization") String authHeader) {
    // Verify internal token — only svc:runtime is allowed
    String token = authHeader != null ? authHeader.replaceFirst("Bearer ", "").trim() : "";
    if (token.isEmpty() || !jwtService.verify(token)) {
      throw new FunctionException("Invalid internal token");
    }
    String account = jwtService.getAccount(token);
    if (!"svc:runtime".equals(account)) {
      throw new FunctionException("Access denied: only svc:runtime token is allowed");
    }

    return functionService.getRuntimeDeployRequest(projectId, name);
  }
}
