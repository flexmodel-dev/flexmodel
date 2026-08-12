package dev.flexmodel.open;

import dev.flexmodel.codegen.entity.Bucket;
import dev.flexmodel.storage.FileItem;
import dev.flexmodel.common.NotFoundException;
import dev.flexmodel.storage.BucketService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;

import java.io.InputStream;
import java.io.OutputStream;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Open API — 对象存储操作。
 * <p>
 * 路径前缀 {@code /open/{projectId}/buckets/{bucketName}/objects}，
 * 面向终端用户（IdP 用户 / open scope API Key）。
 * 仅暴露对象读写操作，不暴露 bucket CRUD（admin only）。
 *
 * @author cjbi
 */
@ApplicationScoped
@Path("/open/{projectId}/buckets/{bucketName}/objects")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OpenStorageResource {

  private static final String OWNER_TYPE = "PROJECT";

  @Inject
  BucketService bucketService;

  @GET
  public List<FileItem> listObjects(
    @PathParam("projectId") String projectId,
    @PathParam("bucketName") String bucketName,
    @QueryParam("prefix") String prefix
  ) {
    Bucket bucket = resolveBucket(projectId, bucketName);
    return bucketService.listFiles(bucket, prefix != null ? prefix : "");
  }

  @GET
  @Path("{path: .*}")
  @Produces(MediaType.APPLICATION_OCTET_STREAM)
  public Response downloadObject(
    @PathParam("projectId") String projectId,
    @PathParam("bucketName") String bucketName,
    @PathParam("path") String path
  ) {
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

  @HEAD
  @Path("{path: .*}")
  public Response headObject(
    @PathParam("projectId") String projectId,
    @PathParam("bucketName") String bucketName,
    @PathParam("path") String path
  ) {
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

  @GET
  @Path("{path: .*}/metadata")
  public FileItem getObjectMetadata(
    @PathParam("projectId") String projectId,
    @PathParam("bucketName") String bucketName,
    @PathParam("path") String path
  ) {
    Bucket bucket = resolveBucket(projectId, bucketName);
    return bucketService.getFile(bucket, path);
  }

  @PUT
  @Path("{path: .*}")
  @Consumes(MediaType.APPLICATION_OCTET_STREAM)
  public Response uploadObject(
    @PathParam("projectId") String projectId,
    @PathParam("bucketName") String bucketName,
    @PathParam("path") String path,
    @QueryParam("folder") @DefaultValue("false") boolean folder,
    @HeaderParam("Content-Length") long contentLength,
    InputStream body
  ) {
    Bucket bucket = resolveBucket(projectId, bucketName);
    String objectPath = folder && !path.endsWith("/") ? path + "/" : path;
    bucketService.uploadFile(bucket, objectPath, body, contentLength);
    return Response.ok().build();
  }

  @DELETE
  @Path("{path: .*}")
  public Response deleteObject(
    @PathParam("projectId") String projectId,
    @PathParam("bucketName") String bucketName,
    @PathParam("path") String path
  ) {
    Bucket bucket = resolveBucket(projectId, bucketName);
    bucketService.deleteFile(bucket, path);
    return Response.noContent().build();
  }

  private Bucket resolveBucket(String projectId, String bucketName) {
    return bucketService.getBucket(OWNER_TYPE, projectId, bucketName)
      .orElseThrow(() -> new NotFoundException("Bucket not found: " + bucketName));
  }
}
