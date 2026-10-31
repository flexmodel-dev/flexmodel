package dev.flexmodel.apps.template;

import dev.flexmodel.apps.apppackage.AppPackageService;

import dev.flexmodel.apps.apppackage.dto.AppPackage.PreviewResponse;
import dev.flexmodel.apps.template.dto.AppTemplate;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * 内置应用模板：目录列表与从模板创建导入预览。
 *
 * <p>实例化只做服务端暂存与预览，创建应用仍由既有导入 commit 接口完成。</p>
 */
@Path("/app-packages/templates")
@Tag(name = "应用", description = "内置应用模板")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Blocking
public class AppTemplateResource {
  @Inject
  AppTemplateCatalog catalog;
  @Inject
  AppPackageService service;

  @Operation(summary = "获取内置应用模板列表")
  @GET
  public List<AppTemplate.Summary> list() {
    service.requireAuthenticatedUser();
    return catalog.summaries();
  }

  @Operation(summary = "从内置模板创建导入预览")
  @POST
  @Path("{templateId}/imports")
  public PreviewResponse instantiate(@PathParam("templateId") String templateId) {
    String user = service.requireAuthenticatedUser();
    AppTemplateCatalog.Template template = catalog.require(templateId);
    try (InputStream input = catalog.open(template)) {
      return service.instantiateTemplate(user, template.summary().id(), template.summary().name(), input);
    } catch (IOException error) {
      throw new WebApplicationException("无法读取内置模板包", error, 500);
    }
  }
}
