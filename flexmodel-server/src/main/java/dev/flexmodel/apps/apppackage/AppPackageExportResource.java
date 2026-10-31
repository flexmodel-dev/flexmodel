package dev.flexmodel.apps.apppackage;

import dev.flexmodel.apps.apppackage.dto.AppPackage.ExportRequest;
import dev.flexmodel.common.ValidationException;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

/**
 * 应用整包导出：全部验证通过后才开始下载，临时文件流式发送后删除。
 *
 * <p>成功响应是二进制 ZIP，异常响应必须是 JSON，否则前端无法拿到失败原因。
 */
@Path("/apps/{appId}/package-exports")
@Tag(name = "应用", description = "应用整包导出")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Blocking
public class AppPackageExportResource {
  static final String ZIP = "application/zip";

  @Inject
  AppPackageService service;

  @POST
  @Produces(ZIP)
  public Response export(@PathParam("appId") String appId, ExportRequest request) {
    AppPackageService.ExportedFile exported = service.export(appId, request);
    String ascii = exported.fileName().replaceAll("[^\\x20-\\x7e]", "_").replace("\"", "'");
    String encoded = URLEncoder.encode(exported.fileName(), StandardCharsets.UTF_8).replace("+", "%20");
    StreamingOutput body = output -> {
      try (InputStream stream = Files.newInputStream(exported.file())) {
        stream.transferTo(output);
      } finally {
        try {
          Files.deleteIfExists(exported.file());
        } catch (IOException suppressed) { /* 定时清理兜底 */ }
      }
    };
    return Response.ok(body, ZIP)
      .header("Content-Disposition", "attachment; filename=\"" + ascii + "\"; filename*=UTF-8''" + encoded)
      .header("Content-Length", exported.size())
      .header("Cache-Control", "no-store")
      .build();
  }

  @ServerExceptionMapper
  public Response validation(ValidationException error) {
    return failure(Response.Status.BAD_REQUEST.getStatusCode(), error.getMessage());
  }

  @ServerExceptionMapper
  public Response rejected(WebApplicationException error) {
    Response response = error.getResponse();
    String message = error.getMessage();
    if (message == null || message.isBlank()) {
      message = response.getStatusInfo().getReasonPhrase();
    }
    return failure(response.getStatus(), message);
  }

  private Response failure(int status, String message) {
    return Response.status(status).type(MediaType.APPLICATION_JSON)
      .entity(Map.of("code", -1, "success", false, "message", message == null ? "" : message))
      .build();
  }
}
