package dev.flexmodel.storage;

import dev.flexmodel.codegen.entity.Bucket;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.media.SchemaProperty;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * Bucket 管理 REST API
 *
 * @author cjbi
 */
@Tag(name = "存储 Bucket", description = "存储 Bucket 管理和文件操作")
@Path("/projects/{projectId}/buckets")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BucketResource {

  private static final String OWNER_TYPE = "PROJECT";

  @Inject
  BucketService bucketService;

  // ==================== Bucket CRUD ====================

  @APIResponse(name = "200", responseCode = "200", description = "OK",
    content = @Content(mediaType = "application/json",
      schema = @Schema(type = SchemaType.ARRAY, implementation = Bucket.class)))
  @Operation(summary = "获取所有 Bucket")
  @GET
  public List<Bucket> findAll(@PathParam("projectId") String projectId) {
    return bucketService.listBuckets(OWNER_TYPE, projectId);
  }

  @APIResponse(name = "200", responseCode = "200", description = "OK",
    content = @Content(mediaType = "application/json",
      schema = @Schema(implementation = Bucket.class)))
  @Operation(summary = "获取单个 Bucket")
  @GET
  @Path("/{bucketName}")
  public Bucket findOne(
    @PathParam("projectId") String projectId,
    @PathParam("bucketName") String bucketName) {
    return bucketService.getBucket(OWNER_TYPE, projectId, bucketName).orElse(null);
  }

  @APIResponse(name = "200", responseCode = "200", description = "OK",
    content = @Content(mediaType = "application/json",
      schema = @Schema(implementation = Bucket.class)))
  @Operation(summary = "创建 Bucket")
  @POST
  public Bucket create(
    @PathParam("projectId") String projectId,
    Bucket bucket) {
    return bucketService.createBucket(OWNER_TYPE, projectId, bucket);
  }

  @APIResponse(name = "200", responseCode = "200", description = "OK",
    content = @Content(mediaType = "application/json",
      schema = @Schema(implementation = Bucket.class)))
  @Operation(summary = "更新 Bucket")
  @PUT
  @Path("/{bucketName}")
  public Bucket update(
    @PathParam("projectId") String projectId,
    @PathParam("bucketName") String bucketName,
    Bucket bucket) {
    return bucketService.updateBucket(OWNER_TYPE, projectId, bucketName, bucket);
  }

  @APIResponse(name = "200", responseCode = "200", description = "OK")
  @Operation(summary = "删除 Bucket")
  @DELETE
  @Path("/{bucketName}")
  public void delete(
    @PathParam("projectId") String projectId,
    @PathParam("bucketName") String bucketName,
    @QueryParam("force") @DefaultValue("false") boolean force) {
    bucketService.deleteBucket(OWNER_TYPE, projectId, bucketName, force);
  }

  // ==================== 文件操作 ====================

  @APIResponse(name = "200", responseCode = "200", description = "OK",
    content = @Content(mediaType = "application/json",
      schema = @Schema(type = SchemaType.ARRAY, implementation = FileItem.class)))
  @Operation(summary = "列出文件")
  @GET
  @Path("/{bucketName}/files")
  public List<FileItem> listFiles(
    @PathParam("projectId") String projectId,
    @PathParam("bucketName") String bucketName,
    @QueryParam("path") String path) {
    Bucket bucket = resolveBucket(projectId, bucketName);
    return bucketService.listFiles(bucket, path != null ? path : "");
  }

  @APIResponse(name = "200", responseCode = "200", description = "OK",
    content = @Content(mediaType = "application/json",
      schema = @Schema(implementation = FileItem.class)))
  @Operation(summary = "获取文件信息")
  @GET
  @Path("/{bucketName}/files/info")
  public FileItem getFile(
    @PathParam("projectId") String projectId,
    @PathParam("bucketName") String bucketName,
    @QueryParam("path") @NotBlank String path) {
    Bucket bucket = resolveBucket(projectId, bucketName);
    return bucketService.getFile(bucket, path);
  }

  @APIResponse(name = "200", responseCode = "200", description = "OK")
  @Operation(summary = "上传文件")
  @POST
  @Path("/{bucketName}/files/upload")
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  public Response uploadFile(
    @PathParam("projectId") String projectId,
    @PathParam("bucketName") String bucketName,
    @QueryParam("path") @NotBlank String path,
    @org.jboss.resteasy.reactive.RestForm("file") @org.jboss.resteasy.reactive.PartType(MediaType.APPLICATION_OCTET_STREAM) InputStream fileStream,
    @org.jboss.resteasy.reactive.RestForm("fileSize") Long fileSize) {
    Bucket bucket = resolveBucket(projectId, bucketName);
    bucketService.uploadFile(bucket, path, fileStream, fileSize != null ? fileSize : 0);
    return Response.ok().build();
  }

  @APIResponse(name = "200", responseCode = "200", description = "OK")
  @Operation(summary = "删除文件")
  @DELETE
  @Path("/{bucketName}/files/delete")
  public void deleteFile(
    @PathParam("projectId") String projectId,
    @PathParam("bucketName") String bucketName,
    @QueryParam("path") @NotBlank String path) {
    Bucket bucket = resolveBucket(projectId, bucketName);
    bucketService.deleteFile(bucket, path);
  }

  @APIResponse(name = "200", responseCode = "200", description = "OK")
  @Operation(summary = "创建文件夹")
  @POST
  @Path("/{bucketName}/folders/create")
  public void createFolder(
    @PathParam("projectId") String projectId,
    @PathParam("bucketName") String bucketName,
    @QueryParam("path") @NotBlank String path) {
    Bucket bucket = resolveBucket(projectId, bucketName);
    bucketService.createFolder(bucket, path);
  }

  @APIResponse(name = "200", responseCode = "200", description = "OK",
    content = @Content(mediaType = "application/json",
      schema = @Schema(properties = @SchemaProperty(name = "exists", description = "是否存在"))))
  @Operation(summary = "检查文件是否存在")
  @GET
  @Path("/{bucketName}/files/exists")
  public Response exists(
    @PathParam("projectId") String projectId,
    @PathParam("bucketName") String bucketName,
    @QueryParam("path") @NotBlank String path) {
    Bucket bucket = resolveBucket(projectId, bucketName);
    boolean fileExists = bucketService.exists(bucket, path);
    return Response.ok().entity(new ExistsResponse(fileExists)).build();
  }

  @APIResponse(name = "200", responseCode = "200", description = "OK",
    content = @Content(mediaType = "application/json",
      schema = @Schema(properties = @SchemaProperty(name = "size", description = "文件大小"))))
  @Operation(summary = "获取文件大小")
  @GET
  @Path("/{bucketName}/files/size")
  public Response getFileSize(
    @PathParam("projectId") String projectId,
    @PathParam("bucketName") String bucketName,
    @QueryParam("path") @NotBlank String path) {
    Bucket bucket = resolveBucket(projectId, bucketName);
    long size = bucketService.getFileSize(bucket, path);
    return Response.ok().entity(new FileSizeResponse(size)).build();
  }

  @APIResponse(name = "200", responseCode = "200", description = "文件内容",
    content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM))
  @Operation(summary = "下载文件")
  @GET
  @Path("/{bucketName}/files/download")
  @Produces(MediaType.APPLICATION_OCTET_STREAM)
  public Response downloadFile(
    @PathParam("projectId") String projectId,
    @PathParam("bucketName") String bucketName,
    @QueryParam("path") @NotBlank String path) {
    Bucket bucket = resolveBucket(projectId, bucketName);
    InputStream inputStream = bucketService.downloadFile(bucket, path);
    String fileName = path.substring(path.lastIndexOf('/') + 1);
    StreamingOutput stream = (OutputStream output) -> {
      byte[] buffer = new byte[8192];
      int bytesRead;
      while ((bytesRead = inputStream.read(buffer)) != -1) {
        output.write(buffer, 0, bytesRead);
      }
      inputStream.close();
    };
    return Response.ok(stream)
      .header("Content-Disposition", "attachment; filename=\"" + fileName + "\"")
      .build();
  }

  // ==================== 内部方法 ====================

  private Bucket resolveBucket(String projectId, String bucketName) {
    return bucketService.getBucket(OWNER_TYPE, projectId, bucketName)
      .orElseThrow(() -> new RuntimeException("Bucket not found: " + bucketName));
  }

  public static class ExistsResponse {
    public boolean exists;

    public ExistsResponse(boolean exists) {
      this.exists = exists;
    }
  }

  public static class FileSizeResponse {
    public long size;

    public FileSizeResponse(long size) {
      this.size = size;
    }
  }
}
