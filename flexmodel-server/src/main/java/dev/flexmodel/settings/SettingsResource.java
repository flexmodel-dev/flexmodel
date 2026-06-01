package dev.flexmodel.settings;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import dev.flexmodel.settings.SettingsService;

/**
 * @author cjbi
 */
@Tag(name = "设置", description = "系统设置")
@Path("/settings")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SettingsResource {

  @Inject
  SettingsService settingsService;

  @Operation(summary = "获取设置")
  @GET
  public Settings getSettings() {
    return settingsService.getSettings();
  }

  @Operation(summary = "保存设置")
  @PATCH
  public Settings saveSettings(Settings settings) {
    return settingsService.saveSettings(settings);
  }

}
