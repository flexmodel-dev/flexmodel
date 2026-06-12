package dev.flexmodel.storage;

import dev.flexmodel.codegen.entity.Bucket;
import dev.flexmodel.storage.config.StorageProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * 存储操作工厂类
 * <p>
 * 根据 Bucket 对象创建带路径前缀的 StorageOperations 实例。
 * 路径规则：{ownerType}/{ownerId}/{bucketName}
 *
 * @author cjbi
 */
@ApplicationScoped
public class StorageOperationsFactory {

  @Inject
  StorageProvider storageProvider;

  /**
   * 构建存储前缀：{ownerType}/{ownerId}/{bucketName}
   */
  private String buildPrefix(Bucket bucket) {
    return bucket.getOwnerType() + "/" + bucket.getOwnerId() + "/" + bucket.getName();
  }

  /**
   * 根据 Bucket 创建带路径前缀的 StorageOperations
   *
   * @param bucket Bucket 实体
   * @return 带前缀上下文的 StorageOperations
   */
  public StorageOperations forBucket(Bucket bucket) {
    return storageProvider.getBackend().createOperations(buildPrefix(bucket));
  }
}
