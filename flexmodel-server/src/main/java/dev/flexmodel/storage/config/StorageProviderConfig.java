package dev.flexmodel.storage.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.util.Optional;

/**
 * 存储后端配置映射
 * <p>
 * 通过 application.properties 配置存储后端参数。
 * 示例：
 * <pre>
 * flexmodel.storage.type=local
 * flexmodel.storage.local-path=./storage
 * flexmodel.storage.read-only=false
 *
 * flexmodel.storage.type=s3
 * flexmodel.storage.s3-access-key=xxx
 * flexmodel.storage.s3-secret-key=xxx
 * flexmodel.storage.s3-bucket=flexmodel
 * flexmodel.storage.s3-region=us-east-1
 * flexmodel.storage.s3-endpoint=http://localhost:9000
 * flexmodel.storage.s3-path-style=true
 * </pre>
 *
 * @author cjbi
 */
@ConfigMapping(prefix = "flexmodel.storage")
public interface StorageProviderConfig {

  /**
   * 存储后端类型：local（默认）或 s3
   */
  @WithDefault("local")
  String type();

  /**
   * 是否只读模式
   */
  @WithName("read-only")
  @WithDefault("false")
  boolean readOnly();

  /**
   * 本地存储基础路径
   */
  @WithName("local-path")
  @WithDefault("./storage")
  String localPath();

  /**
   * S3 Access Key
   */
  @WithName("s3-access-key")
  Optional<String> s3AccessKey();

  /**
   * S3 Secret Key
   */
  @WithName("s3-secret-key")
  Optional<String> s3SecretKey();

  /**
   * S3 真实 Bucket 名称
   */
  @WithName("s3-bucket")
  Optional<String> s3Bucket();

  /**
   * S3 Region
   */
  @WithName("s3-region")
  @WithDefault("us-east-1")
  String s3Region();

  /**
   * S3 Endpoint（兼容 MinIO 等）
   */
  @WithName("s3-endpoint")
  Optional<String> s3Endpoint();

  /**
   * S3 是否使用 Path Style
   */
  @WithName("s3-path-style")
  @WithDefault("false")
  boolean s3PathStyle();
}
