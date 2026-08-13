package dev.flexmodel.settings;

import dev.flexmodel.common.FlexmodelConfig;
import dev.flexmodel.storage.config.StorageProvider;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.media.SchemaProperty;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.Map;

/**
 * @author cjbi
 */
@Tag(name = "系统", description = "系统信息")
@Slf4j
@Path("/global")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class GlobalResource {

  @Inject
  SettingsService settingsService;

  @Inject
  FlexmodelConfig config;

  @Inject
  StorageProvider storageProvider;

  @APIResponse(
    name = "200",
    responseCode = "200",
    description = "OK",
    content = {@Content(
      mediaType = "application/json",
      schema = @Schema(
        properties = {
          @SchemaProperty(name = "settings", description = "系统设置"),
          @SchemaProperty(name = "apiRootPath", description = "API 根路径"),
          @SchemaProperty(name = "storageProvider", description = "存储后端信息"),
          @SchemaProperty(name = "projectBaseDomain", description = "项目基础域名，用于 CORS 和 URL 拼接"),
          @SchemaProperty(name = "routingMode", description = "路由模式：path 为路径模式，subdomain 为子域名模式"),
          @SchemaProperty(name = "edgeUrlTemplate", description = "边缘函数调用 URL，由 routingMode + projectBaseDomain 自动推导"),
          @SchemaProperty(name = "pagesUrlTemplate", description = "Pages 站点 URL，由 routingMode + projectBaseDomain 自动推导"),
        }
      )
    )
    })
  @Operation(summary = "获取系统配置")
  @GET
  @Path("/profile")
  @PermitAll
  public Map<String, Object> getProfile() {
    return Map.of(
      "settings", settingsService.getSettings(),
      "apiRootPath", config.apiRootPath(),
      "storageProvider", storageProvider.getProviderInfo(),
      "projectBaseDomain", config.projectBaseDomain().orElse(""),
      "routingMode", config.isSubdomainRouting() ? "subdomain" : "path",
      "edgeUrlTemplate", config.edgeUrlTemplate(),
      "pagesUrlTemplate", config.pagesUrlTemplate()
    );
  }

}
