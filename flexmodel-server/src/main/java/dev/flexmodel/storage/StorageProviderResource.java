package dev.flexmodel.storage;

import dev.flexmodel.storage.config.StorageProvider;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.Map;

/**
 * 存储提供者信息 REST API
 *
 * @author cjbi
 */
@Tag(name = "存储提供者", description = "存储后端信息查询")
@Path("/storage/provider")
@Produces(MediaType.APPLICATION_JSON)
public class StorageProviderResource {

  @Inject
  StorageProvider storageProvider;

  @APIResponse(name = "200", responseCode = "200", description = "OK")
  @Operation(summary = "获取当前存储后端信息")
  @GET
  public Map<String, Object> getProviderInfo() {
    return storageProvider.getProviderInfo();
  }
}
