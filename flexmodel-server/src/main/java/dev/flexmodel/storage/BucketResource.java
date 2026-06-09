package dev.flexmodel.storage;

import dev.flexmodel.codegen.entity.Bucket;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.io.InputStream;
import java.io.OutputStream;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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

  // ==================== 对象操作 ====================

  @APIResponse(name = "200", responseCode = "200", description = "OK",
    content = @Content(mediaType = "application/json",
      schema = @Schema(type = SchemaType.ARRAY, implementation = FileItem.class)))
  @Operation(summary = "列出对象")
  @GET
  @Path("/{bucketName}/objects")
  public List<FileItem> listObjects(
    @PathParam("projectId") String projectId,
    @PathParam("bucketName") String bucketName,
    @QueryParam("prefix") String prefix) {
    Bucket bucket = resolveBucket(projectId, bucketName);
    return bucketService.listFiles(bucket, prefix != null ? prefix : "");
  }

  @APIResponse(name = "200", responseCode = "200", description = "上传成功")
  @Operation(summary = "上传对象")
  @PUT
  @Path("/{bucketName}/objects/{path: .*}")
  @Consumes(MediaType.APPLICATION_OCTET_STREAM)
  public Response uploadObject(
    @PathParam("projectId") String projectId,
    @PathParam("bucketName") String bucketName,
    @PathParam("path") String path,
    @QueryParam("folder") @DefaultValue("false") boolean folder,
    @HeaderParam("Content-Length") long contentLength,
    InputStream body) {
    Bucket bucket = resolveBucket(projectId, bucketName);
    // When folder=true, append trailing slash to mark as directory
    String objectPath = folder && !path.endsWith("/") ? path + "/" : path;
    bucketService.uploadFile(bucket, objectPath, body, contentLength);
    return Response.ok().build();
  }

  @APIResponse(name = "200", responseCode = "200", description = "文件内容",
    content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM))
  @Operation(summary = "下载对象")
  @GET
  @Path("/{bucketName}/objects/{path: .*}")
  @Produces(MediaType.APPLICATION_OCTET_STREAM)
  public Response downloadObject(
    @PathParam("projectId") String projectId,
    @PathParam("bucketName") String bucketName,
    @PathParam("path") String path) {
    Bucket bucket = resolveBucket(projectId, bucketName);
    InputStream inputStream = bucketService.getInputStream(bucket, path);
    String fileName = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
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

  @APIResponse(name = "200", responseCode = "200", description = "删除成功")
  @Operation(summary = "删除对象")
  @DELETE
  @Path("/{bucketName}/objects/{path: .*}")
  public Response deleteObject(
    @PathParam("projectId") String projectId,
    @PathParam("bucketName") String bucketName,
    @PathParam("path") String path) {
    Bucket bucket = resolveBucket(projectId, bucketName);
    bucketService.deleteFile(bucket, path);
    return Response.noContent().build();
  }

  @APIResponse(name = "200", responseCode = "200", description = "对象存在")
  @APIResponse(name = "404", responseCode = "404", description = "对象不存在")
  @Operation(summary = "检查对象是否存在并获取元数据头")
  @HEAD
  @Path("/{bucketName}/objects/{path: .*}")
  public Response headObject(
    @PathParam("projectId") String projectId,
    @PathParam("bucketName") String bucketName,
    @PathParam("path") String path) {
    Bucket bucket = resolveBucket(projectId, bucketName);
    FileItem item = bucketService.getFile(bucket, path);
    if (item == null) {
      return Response.status(Response.Status.NOT_FOUND).build();
    }
    Response.ResponseBuilder rb = Response.ok();
    if (item.getSize() != null) {
      rb.header("Content-Length", item.getSize());
    }
    if (item.getLastModified() != null) {
      rb.header("Last-Modified", DateTimeFormatter.RFC_1123_DATE_TIME
        .withZone(ZoneOffset.UTC).format(item.getLastModified()));
    }
    return rb.build();
  }

  @APIResponse(name = "200", responseCode = "200", description = "OK",
    content = @Content(mediaType = "application/json",
      schema = @Schema(implementation = FileItem.class)))
  @Operation(summary = "获取对象元数据")
  @GET
  @Path("/{bucketName}/objects/{path}/metadata")
  public FileItem getObjectMetadata(
    @PathParam("projectId") String projectId,
    @PathParam("bucketName") String bucketName,
    @PathParam("path") String path) {
    Bucket bucket = resolveBucket(projectId, bucketName);
    return bucketService.getFile(bucket, path);
  }

  // ==================== 内部方法 ====================

  private Bucket resolveBucket(String projectId, String bucketName) {
    return bucketService.getBucket(OWNER_TYPE, projectId, bucketName)
      .orElseThrow(() -> new RuntimeException("Bucket not found: " + bucketName));
  }
}
