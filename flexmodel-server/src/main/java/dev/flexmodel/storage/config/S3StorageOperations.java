package dev.flexmodel.storage.config;

import dev.flexmodel.storage.FileItem;
import dev.flexmodel.storage.StorageOperations;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * S3 文件存储实现 (AWS SDK v2)
 * <p>
 * 兼容 AWS S3、MinIO、阿里云 OSS 等 S3 兼容存储。
 * 使用外部注入的 S3Client 和前缀路径，支持多租户 Bucket 隔离。
 *
 * @author cjbi
 */
public class S3StorageOperations implements StorageOperations {

  private final S3Client s3Client;
  private final String bucket;
  private final String prefix;

  /**
   * 使用已有的 S3Client 和前缀路径构造
   *
   * @param s3Client 已配置的 S3 客户端
   * @param bucket   真实 S3 Bucket 名称
   * @param prefix   前缀路径（如 "PROJECT/project-a/images"），所有操作的 key 均以此为前缀
   */
  public S3StorageOperations(S3Client s3Client, String bucket, String prefix) {
    this.s3Client = s3Client;
    this.bucket = bucket;
    this.prefix = normalizePrefix(prefix);
  }

  @Override
  public List<FileItem> listFiles(String path) {
    String fullPrefix = prefix + normalizeKey(path);
    if (!fullPrefix.isEmpty() && !fullPrefix.endsWith("/")) {
      fullPrefix = fullPrefix + "/";
    }
    List<FileItem> items = new ArrayList<>();

    try {
      ListObjectsV2Request request = ListObjectsV2Request.builder()
        .bucket(bucket)
        .prefix(fullPrefix)
        .delimiter("/")
        .build();

      ListObjectsV2Response response = s3Client.listObjectsV2(request);

      // Common prefixes (folders)
      for (CommonPrefix cp : response.commonPrefixes()) {
        String folderPath = cp.prefix();
        String folderName = folderPath.substring(fullPrefix.length(), folderPath.length() - 1);
        if (!folderName.isEmpty()) {
          items.add(FileItem.builder()
            .name(folderName)
            .type(FileItem.FileType.folder)
            .path(stripPrefix(folderPath))
            .build());
        }
      }

      // Objects (files)
      for (S3Object obj : response.contents()) {
        String key = obj.key();
        if (key.equals(fullPrefix)) {
          continue; // Skip the directory placeholder itself
        }
        String fileName = key.substring(fullPrefix.length());
        if (fileName.contains("/")) {
          continue; // Skip nested objects, they'll be under commonPrefixes
        }
        items.add(FileItem.builder()
          .name(fileName)
          .type(FileItem.FileType.file)
          .size(obj.size())
          .lastModified(obj.lastModified())
          .path(stripPrefix(key))
          .build());
      }
    } catch (S3Exception e) {
      throw new RuntimeException("Failed to list files in S3: " + path, e);
    }

    return items;
  }

  @Override
  public FileItem getFile(String path) {
    String key = prefix + normalizeKey(path);
    try {
      HeadObjectResponse head = s3Client.headObject(b -> b.bucket(bucket).key(key));
      return FileItem.builder()
        .name(key.substring(key.lastIndexOf('/') + 1))
        .type(FileItem.FileType.file)
        .size(head.contentLength())
        .lastModified(head.lastModified())
        .path(stripPrefix(key))
        .build();
    } catch (NoSuchKeyException e) {
      // Check if it's a folder
      return tryGetFolder(path, key);
    } catch (S3Exception e) {
      throw new RuntimeException("Failed to get file info from S3: " + path, e);
    }
  }

  private FileItem tryGetFolder(String path, String key) {
    try {
      ListObjectsV2Request request = ListObjectsV2Request.builder()
        .bucket(bucket)
        .prefix(key.endsWith("/") ? key : key + "/")
        .maxKeys(1)
        .build();
      ListObjectsV2Response response = s3Client.listObjectsV2(request);
      if (!response.contents().isEmpty() || !response.commonPrefixes().isEmpty()) {
        return FileItem.builder()
          .name(key.substring(key.lastIndexOf('/') + 1))
          .type(FileItem.FileType.folder)
          .path(stripPrefix(key))
          .build();
      }
    } catch (S3Exception ignored) {
    }
    return null;
  }

  @Override
  public void uploadFile(String path, InputStream inputStream, long size) {
    String key = prefix + normalizeKey(path);
    try {
      s3Client.putObject(
        PutObjectRequest.builder()
          .bucket(bucket)
          .key(key)
          .contentLength(size > 0 ? size : null)
          .build(),
        RequestBody.fromInputStream(inputStream, size)
      );
    } catch (S3Exception e) {
      throw new RuntimeException("Failed to upload file to S3: " + path, e);
    }
  }

  @Override
  public void deleteFile(String path) {
    String key = prefix + normalizeKey(path);
    try {
      // Check if there are objects with this prefix (folder)
      ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
        .bucket(bucket)
        .prefix(key.endsWith("/") ? key : key + "/")
        .build();
      ListObjectsV2Response listResponse = s3Client.listObjectsV2(listRequest);

      if (!listResponse.contents().isEmpty()) {
        // Delete all objects under this prefix (recursive delete)
        List<ObjectIdentifier> objectsToDelete = listResponse.contents().stream()
          .map(obj -> ObjectIdentifier.builder().key(obj.key()).build())
          .collect(Collectors.toList());

        s3Client.deleteObjects(
          DeleteObjectsRequest.builder()
            .bucket(bucket)
            .delete(Delete.builder().objects(objectsToDelete).build())
            .build()
        );

        // Handle pagination for large folders
        while (listResponse.isTruncated()) {
          listResponse = s3Client.listObjectsV2(
            ListObjectsV2Request.builder()
              .bucket(bucket)
              .prefix(key.endsWith("/") ? key : key + "/")
              .continuationToken(listResponse.nextContinuationToken())
              .build()
          );
          if (!listResponse.contents().isEmpty()) {
            List<ObjectIdentifier> nextBatch = listResponse.contents().stream()
              .map(obj -> ObjectIdentifier.builder().key(obj.key()).build())
              .collect(Collectors.toList());
            s3Client.deleteObjects(
              DeleteObjectsRequest.builder()
                .bucket(bucket)
                .delete(Delete.builder().objects(nextBatch).build())
                .build()
            );
          }
        }
      }

      // Also delete the object itself (in case it's a file, not a folder)
      s3Client.deleteObject(b -> b.bucket(bucket).key(key));
    } catch (S3Exception e) {
      throw new RuntimeException("Failed to delete file from S3: " + path, e);
    }
  }

  @Override
  public InputStream getInputStream(String path) {
    String key = prefix + normalizeKey(path);
    try {
      return s3Client.getObject(b -> b.bucket(bucket).key(key));
    } catch (S3Exception e) {
      throw new RuntimeException("Failed to download file from S3: " + path, e);
    }
  }

  /**
   * 去除前缀，返回相对路径
   */
  private String stripPrefix(String key) {
    if (prefix != null && !prefix.isEmpty() && key.startsWith(prefix)) {
      return key.substring(prefix.length());
    }
    return key;
  }

  private static String normalizeKey(String path) {
    if (path.startsWith("/")) {
      path = path.substring(1);
    }
    return path;
  }

  private static String normalizePrefix(String path) {
    String p = normalizeKey(path);
    if (!p.isEmpty() && !p.endsWith("/")) {
      p = p + "/";
    }
    return p;
  }
}