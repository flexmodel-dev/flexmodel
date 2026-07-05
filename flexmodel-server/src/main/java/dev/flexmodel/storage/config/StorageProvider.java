package dev.flexmodel.storage.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 存储提供者
 * <p>
 * 应用级别单例，持有 StorageBackend 实例。
 * 由 StorageProviderInitializer 在启动时初始化。
 *
 * <p>S3Client 由 quarkus-amazon-s3 扩展自动创建并注入，
 * 连接参数通过 application.properties 中的 quarkus.s3.* 配置。
 *
 * @author cjbi
 */
@ApplicationScoped
public class StorageProvider {

  @Inject
  S3Client s3Client;

  private StorageBackend backend;
  private StorageProviderConfig config;

  /**
   * 初始化存储后端（由 StorageProviderInitializer 调用）
   */
  public void initialize(StorageProviderConfig config) {
    this.config = config;

    if ("s3".equalsIgnoreCase(config.type())) {
      this.backend = createS3Backend(config);
    } else {
      this.backend = createLocalBackend(config);
    }

    this.backend.validate();
  }

  public StorageBackend getBackend() {
    return backend;
  }

  public boolean isReadOnly() {
    return config != null && config.readOnly();
  }

  /**
   * 获取存储提供者信息（用于 API 展示）
   */
  public Map<String, Object> getProviderInfo() {
    Map<String, Object> info = new LinkedHashMap<>();
    info.put("type", backend != null ? backend.getType() : "unknown");
    info.put("readOnly", isReadOnly());

    if (backend instanceof S3Backend s3) {
      info.put("bucket", s3.getBucket());
      if (s3.getEndpoint() != null) {
        info.put("endpoint", s3.getEndpoint());
      }
    } else if (backend instanceof LocalBackend local) {
      info.put("localPath", local.getBasePath().toString());
    }

    return info;
  }

  private LocalBackend createLocalBackend(StorageProviderConfig config) {
    return new LocalBackend(config.localPath(), config.readOnly());
  }

  private S3Backend createS3Backend(StorageProviderConfig config) {
    String bucket = config.s3Bucket()
      .orElseThrow(() -> new RuntimeException("S3 storage requires 'flexmodel.storage.s3-bucket'"));
    String endpoint = config.s3Endpoint().orElse(null);

    return new S3Backend(s3Client, bucket, endpoint, config.readOnly());
  }
}
