package dev.flexmodel.storage.config;

import dev.flexmodel.storage.StorageOperations;

/**
 * 存储后端抽象接口
 * <p>
 * 表示底层存储引擎（本地文件系统、S3、OSS 等），不包含 Bucket 概念。
 * 所有操作均基于前缀路径（prefix）。
 *
 * @author cjbi
 */
public interface StorageBackend {

  // ========== 文件操作 ==========

  /**
   * 在指定前缀路径下创建文件操作实例
   *
   * @param prefixPath 前缀路径，如 "PROJECT/project-a/images"
   * @return 带前缀上下文的 StorageOperations
   */
  StorageOperations createOperations(String prefixPath);

  // ========== 容器生命周期 ==========

  /**
   * 创建存储容器（目录/前缀）
   *
   * @param prefix 前缀路径
   */
  void createContainer(String prefix);

  /**
   * 删除存储容器及其下所有内容
   *
   * @param prefix 前缀路径
   */
  void deleteContainer(String prefix);

  /**
   * 检查存储容器是否存在
   *
   * @param prefix 前缀路径
   * @return 是否存在
   */
  boolean containerExists(String prefix);

  // ========== 后端管理 ==========

  /**
   * 验证后端连接/路径是否可用
   */
  void validate();

  /**
   * 获取后端类型标识
   *
   * @return 类型名称，如 "local", "s3"
   */
  String getType();
}
