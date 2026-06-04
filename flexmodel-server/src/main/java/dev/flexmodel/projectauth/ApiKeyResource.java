package dev.flexmodel.projectauth;

import dev.flexmodel.projectauth.dto.ApiKeyResponse;
import dev.flexmodel.projectauth.dto.CreateApiKeyRequest;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Tag(name = "API Key", description = "API Key 管理")
@Path("/projects/{projectId}/api-keys")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ApiKeyResource {

  @Inject
  ApiKeyService apiKeyService;

  @Operation(summary = "获取 API Key 列表")
  @GET
  public List<ApiKeyResponse> list(@PathParam("projectId") String projectId) {
    return apiKeyService.listByProject(projectId);
  }

  @Operation(summary = "创建 API Key")
  @POST
  public ApiKeyResponse create(@PathParam("projectId") String projectId, CreateApiKeyRequest request) {
    return apiKeyService.create(projectId, request);
  }

  @Operation(summary = "重新生成 API Key")
  @POST
  @Path("/{id}/regenerate")
  public ApiKeyResponse regenerate(@PathParam("projectId") String projectId, @PathParam("id") String id) {
    return apiKeyService.regenerate(id);
  }

  @Operation(summary = "删除 API Key")
  @DELETE
  @Path("/{id}")
  public void delete(@PathParam("projectId") String projectId, @PathParam("id") String id) {
    apiKeyService.delete(id);
  }
}
