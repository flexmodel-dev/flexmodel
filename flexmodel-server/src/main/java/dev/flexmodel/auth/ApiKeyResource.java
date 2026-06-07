package dev.flexmodel.auth;

import dev.flexmodel.auth.dto.ApiKeyResponse;
import dev.flexmodel.auth.dto.CreateApiKeyRequest;
import dev.flexmodel.auth.service.ApiKeyService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Tag(name = "API Key", description = "API Key 管理")
@Path("/api-keys")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ApiKeyResource {

  @Inject
  ApiKeyService apiKeyService;

  @Operation(summary = "获取 API Key 列表")
  @GET
  public List<ApiKeyResponse> list() {
    return apiKeyService.listAll();
  }

  @Operation(summary = "创建 API Key")
  @POST
  public ApiKeyResponse create(CreateApiKeyRequest request) {
    return apiKeyService.create(request);
  }

  @Operation(summary = "重新生成 API Key")
  @POST
  @Path("/{id}/regenerate")
  public ApiKeyResponse regenerate(@PathParam("id") String id) {
    return apiKeyService.regenerate(id);
  }

  @Operation(summary = "删除 API Key")
  @DELETE
  @Path("/{id}")
  public void delete(@PathParam("id") String id) {
    apiKeyService.delete(id);
  }
}
