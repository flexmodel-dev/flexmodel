package dev.flexmodel.apps.transfer;

import dev.flexmodel.apps.transfer.dto.AppTransfer.*;
import dev.flexmodel.common.ValidationException;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

@Path("/apps/{appId}/transfers")
@Tag(name = "应用", description = "多表单 Excel 导入导出")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Blocking
public class AppTransferResource {
  @Inject AppTransferService service;
  static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

  @GET @Path("/forms")
  public java.util.List<dev.flexmodel.apps.form.dto.AppFormResponse> forms(@PathParam("appId") String appId) {
    return service.availableForms(appId);
  }

  @POST @Path("/templates") @Produces(XLSX)
  public Response template(@PathParam("appId") String appId, ExportRequest request) {
    return download(service.export(appId, request, true), "flexmodel-template.xlsx");
  }
  @POST @Path("/exports") @Produces(XLSX)
  public Response export(@PathParam("appId") String appId, ExportRequest request) {
    return download(service.export(appId, request, false), "flexmodel-data.xlsx");
  }
  @POST @Path("/imports") @Consumes(MediaType.MULTIPART_FORM_DATA)
  public UploadResponse upload(@PathParam("appId") String appId, @RestForm("file") FileUpload file) {
    if (file == null || !file.fileName().toLowerCase(java.util.Locale.ROOT).endsWith(".xlsx"))
      throw new BadRequestException("请选择 .xlsx 文件");
    return service.upload(appId, file.uploadedFile());
  }
  @POST @Path("/imports/{uploadId}/preview")
  public PreviewResponse preview(@PathParam("appId") String appId, @PathParam("uploadId") String uploadId, PreviewRequest request) {
    return service.preview(appId, uploadId, request);
  }
  @POST @Path("/imports/{importId}/commit")
  public TransferResult commit(@PathParam("appId") String appId, @PathParam("importId") String importId, CommitRequest request) {
    return service.commit(appId, importId, request);
  }
  @GET @Path("/imports/{importId}")
  public Object status(@PathParam("appId") String appId, @PathParam("importId") String importId) { return service.status(appId, importId); }

  private Response download(byte[] bytes, String name) {
    return Response.ok(bytes, XLSX).header("Content-Disposition", "attachment; filename=\"" + name + "\"")
      .header("Cache-Control", "no-store").build();
  }

  @ServerExceptionMapper
  public Response validation(ValidationException error) {
    return Response.status(400).type(MediaType.APPLICATION_JSON).entity(java.util.Map.of("message", error.getMessage())).build();
  }

  @ServerExceptionMapper
  public Response rejected(WebApplicationException error) {
    return Response.status(error.getResponse().getStatus()).type(MediaType.APPLICATION_JSON)
      .entity(java.util.Map.of("message", error.getMessage())).build();
  }
}
