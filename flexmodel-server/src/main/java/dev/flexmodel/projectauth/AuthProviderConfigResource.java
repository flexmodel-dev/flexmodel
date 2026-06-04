package dev.flexmodel.projectauth;

import dev.flexmodel.codegen.entity.AuthProviderConfig;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Tag(name = "认证提供商", description = "认证提供商配置管理")
@Path("/projects/{projectId}/auth-providers")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthProviderConfigResource {

  @Inject
  AuthProviderConfigService authProviderConfigService;

  @Operation(summary = "获取认证提供商列表")
  @GET
  public List<AuthProviderConfig> list(@PathParam("projectId") String projectId) {
    return authProviderConfigService.listByProject(projectId);
  }

  @Operation(summary = "创建认证提供商")
  @POST
  public AuthProviderConfig create(@PathParam("projectId") String projectId, AuthProviderConfig config) {
    return authProviderConfigService.create(projectId, config);
  }

  @Parameter(name = "name", description = "提供商名称", in = ParameterIn.PATH)
  @Operation(summary = "更新认证提供商")
  @PUT
  @Path("/{name}")
  public AuthProviderConfig update(@PathParam("projectId") String projectId, @PathParam("name") String name, AuthProviderConfig config) {
    return authProviderConfigService.update(projectId, name, config);
  }

  @Parameter(name = "name", description = "提供商名称", in = ParameterIn.PATH)
  @Operation(summary = "删除认证提供商")
  @DELETE
  @Path("/{name}")
  public void delete(@PathParam("projectId") String projectId, @PathParam("name") String name) {
    authProviderConfigService.delete(projectId, name);
  }
}
