package dev.flexmodel.apps;

import dev.flexmodel.apps.dto.AppPackage.CommitRequest;
import dev.flexmodel.apps.dto.AppPackage.PreviewResponse;
import dev.flexmodel.apps.dto.AppPackage.TaskResult;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

import static dev.flexmodel.apps.dto.AppPackage.EXTENSION;
import static dev.flexmodel.apps.dto.AppPackage.MAX_PACKAGE_BYTES;

/**
 * 应用整包导入：上传预览、状态查询与幂等确认提交。
 */
@Path("/app-packages/imports")
@Tag(name = "应用", description = "应用整包导入")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Blocking
public class AppPackageImportResource {
  @Inject
  AppPackageService service;
  @Inject
  AppPackageStore store;

  @POST
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  public PreviewResponse upload(@RestForm("file") FileUpload file, @RestForm("fileName") String fileName) {
    String user = service.requireAuthenticatedUser();
    if (file == null || file.fileName() == null
      || !file.fileName().toLowerCase(Locale.ROOT).endsWith(".zip"))
      throw new BadRequestException("请选择 " + EXTENSION + " 文件");
    java.nio.file.Path uploaded = file.uploadedFile();
    long size;
    try {
      size = Files.size(uploaded);
    } catch (IOException error) {
      throw new WebApplicationException("无法读取上传文件", 400);
    }
    if (size > MAX_PACKAGE_BYTES)
      throw new WebApplicationException("应用包不能超过 " + (MAX_PACKAGE_BYTES / 1024 / 1024) + " MiB", 413);
    java.nio.file.Path staged = store.stageZip();
    try {
      Files.copy(uploaded, staged, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException error) {
      try {
        Files.deleteIfExists(staged);
      } catch (IOException suppressed) {
        error.addSuppressed(suppressed);
      }
      throw new IllegalStateException("无法保存上传文件", error);
    }
    return service.upload(user, staged,
      fileName != null && !fileName.isBlank() ? fileName : file.fileName());
  }

  @GET
  @Path("{uploadId}")
  public Object status(@PathParam("uploadId") String uploadId) {
    return service.status(service.requireAuthenticatedUser(), uploadId);
  }

  @POST
  @Path("{uploadId}/commit")
  public TaskResult commit(@PathParam("uploadId") String uploadId, CommitRequest request) {
    return service.commit(service.requireAuthenticatedUser(), uploadId, request);
  }
}
