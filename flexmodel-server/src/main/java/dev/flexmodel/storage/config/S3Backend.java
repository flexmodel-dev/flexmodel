package dev.flexmodel.storage.config;

import dev.flexmodel.storage.StorageOperations;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.*;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

/**
 * S3 存储后端实现
 * <p>
 * 使用一个真实 S3 Bucket，容器对应 Bucket 下的前缀路径。
 * 兼容 AWS S3、MinIO、阿里云 OSS 等 S3 兼容存储。
 *
 * @author cjbi
 */
public class S3Backend implements StorageBackend {

  public static final String TYPE = "s3";

  private final S3Client s3Client;
  private final String bucket;
  private final String endpoint;
  private final boolean readOnly;

  public S3Backend(String accessKey, String secretKey, String bucket, String region,
                   String endpoint, boolean pathStyle, boolean readOnly) {
    this.bucket = bucket;
    this.endpoint = endpoint;
    this.readOnly = readOnly;

    AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
    S3ClientBuilder builder = S3Client.builder()
      .credentialsProvider(StaticCredentialsProvider.create(credentials))
      .region(Region.of(region));

    if (endpoint != null && !endpoint.isEmpty()) {
      builder.endpointOverride(URI.create(endpoint));
      if (pathStyle) {
        builder.forcePathStyle(true);
      }
    }

    this.s3Client = builder.build();
  }

  @Override
  public StorageOperations createOperations(String prefixPath) {
    return new S3StorageOperations(s3Client, bucket, prefixPath);
  }

  @Override
  public void createContainer(String prefix) {
    if (readOnly) {
      throw new StorageReadOnlyException("createContainer");
    }
    String key = normalizePrefix(prefix);
    try {
      s3Client.putObject(
        PutObjectRequest.builder()
          .bucket(bucket)
          .key(key)
          .build(),
        RequestBody.empty()
      );
    } catch (S3Exception e) {
      throw new RuntimeException("Failed to create S3 container prefix: " + prefix, e);
    }
  }

  @Override
  public void deleteContainer(String prefix) {
    if (readOnly) {
      throw new StorageReadOnlyException("deleteContainer");
    }
    String s3Prefix = normalizePrefix(prefix);
    try {
      deleteAllWithPrefix(s3Prefix);
    } catch (S3Exception e) {
      throw new RuntimeException("Failed to delete S3 container: " + prefix, e);
    }
  }

  @Override
  public boolean containerExists(String prefix) {
    String s3Prefix = normalizePrefix(prefix);
    try {
      ListObjectsV2Request request = ListObjectsV2Request.builder()
        .bucket(bucket)
        .prefix(s3Prefix)
        .maxKeys(1)
        .build();
      ListObjectsV2Response response = s3Client.listObjectsV2(request);
      return !response.contents().isEmpty() || !response.commonPrefixes().isEmpty();
    } catch (S3Exception e) {
      return false;
    }
  }

  @Override
  public void validate() {
    try {
      s3Client.headBucket(b -> b.bucket(bucket));
    } catch (S3Exception e) {
      throw new RuntimeException("Failed to validate S3 bucket '" + bucket + "': " + e.getMessage(), e);
    }
  }

  @Override
  public String getType() {
    return TYPE;
  }

  public String getBucket() {
    return bucket;
  }

  public String getEndpoint() {
    return endpoint;
  }

  public boolean isReadOnly() {
    return readOnly;
  }

  private void deleteAllWithPrefix(String prefix) {
    ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
      .bucket(bucket)
      .prefix(prefix)
      .build();
    ListObjectsV2Response listResponse = s3Client.listObjectsV2(listRequest);

    while (!listResponse.contents().isEmpty()) {
      List<ObjectIdentifier> objectsToDelete = listResponse.contents().stream()
        .map(obj -> ObjectIdentifier.builder().key(obj.key()).build())
        .collect(Collectors.toList());

      s3Client.deleteObjects(
        DeleteObjectsRequest.builder()
          .bucket(bucket)
          .delete(Delete.builder().objects(objectsToDelete).build())
          .build()
      );

      if (listResponse.isTruncated()) {
        listResponse = s3Client.listObjectsV2(
          ListObjectsV2Request.builder()
            .bucket(bucket)
            .prefix(prefix)
            .continuationToken(listResponse.nextContinuationToken())
            .build()
        );
      } else {
        break;
      }
    }
  }

  private static String normalizePrefix(String prefix) {
    if (prefix.startsWith("/")) {
      prefix = prefix.substring(1);
    }
    if (!prefix.isEmpty() && !prefix.endsWith("/")) {
      prefix = prefix + "/";
    }
    return prefix;
  }
}
