package dev.flexmodel.storage;

import dev.flexmodel.codegen.entity.Bucket;
import dev.flexmodel.storage.config.StorageProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Bucket 服务
 * <p>
 * 负责 Bucket 的 CRUD、权限校验和文件操作委托。
 * 不关心底层存储实现，仅通过 StorageOperationsFactory 获取操作实例。
 *
 * @author cjbi
 */
@ApplicationScoped
public class BucketService {

  @Inject
  BucketRepository bucketRepository;

  @Inject
  StorageOperationsFactory storageOperationsFactory;

  @Inject
  StorageProvider storageProvider;

  // ==================== Bucket CRUD ====================

  public Bucket createBucket(String ownerType, String ownerId, Bucket bucket) {
    // 检查同名
    Optional<Bucket> existing = bucketRepository.findOne(ownerType, ownerId, bucket.getName());
    if (existing.isPresent()) {
      throw new RuntimeException("Bucket name '" + bucket.getName() + "' already exists");
    }

    // 设置归属
    bucket.setOwnerType(ownerType);
    bucket.setOwnerId(ownerId);

    // 生成 ID
    if (bucket.getId() == null || bucket.getId().isEmpty()) {
      bucket.setId(UUID.randomUUID().toString());
    }

    // 创建底层容器
    String prefix = buildPrefix(bucket);
    storageProvider.getBackend().createContainer(prefix);

    // 保存数据库记录
    return bucketRepository.save(bucket);
  }

  public List<Bucket> listBuckets(String ownerType, String ownerId) {
    return bucketRepository.findByOwner(ownerType, ownerId);
  }

  public Optional<Bucket> getBucket(String ownerType, String ownerId, String bucketName) {
    return bucketRepository.findOne(ownerType, ownerId, bucketName);
  }

  public Bucket updateBucket(String ownerType, String ownerId, String bucketName, Bucket bucket) {
    Optional<Bucket> existing = bucketRepository.findOne(ownerType, ownerId, bucketName);
    if (existing.isEmpty()) {
      throw new RuntimeException("Bucket not found: " + bucketName);
    }
    Bucket old = existing.get();
    // 保留不可变字段
    bucket.setId(old.getId());
    bucket.setOwnerType(ownerType);
    bucket.setOwnerId(ownerId);
    bucket.setName(old.getName()); // name 不可修改
    bucket.setCreatedAt(old.getCreatedAt());
    return bucketRepository.save(bucket);
  }

  public void deleteBucket(String ownerType, String ownerId, String bucketName, boolean force) {
    Bucket bucket = bucketRepository.findOne(ownerType, ownerId, bucketName)
      .orElseThrow(() -> new RuntimeException("Bucket not found: " + bucketName));

    if (!force) {
      // 检查 bucket 是否为空
      StorageOperations ops = storageOperationsFactory.forBucket(bucket);
      List<FileItem> files = ops.listFiles("");
      if (!files.isEmpty()) {
        throw new BucketNotEmptyException(bucketName);
      }
    }

    // 删除底层容器内容（预留异步扩展点）
    deleteBucketContents(bucket);

    // 删除数据库记录
    bucketRepository.delete(ownerType, ownerId, bucketName);
  }

  // ==================== 文件操作委托 ====================

  public List<FileItem> listFiles(Bucket bucket, String path) {
    StorageOperations ops = storageOperationsFactory.forBucket(bucket);
    return ops.listFiles(path);
  }

  public FileItem getFile(Bucket bucket, String path) {
    StorageOperations ops = storageOperationsFactory.forBucket(bucket);
    return ops.getFile(path);
  }

  public void uploadFile(Bucket bucket, String path, InputStream inputStream, long size) {
    StorageOperations ops = storageOperationsFactory.forBucket(bucket);
    ops.uploadFile(path, inputStream, size);
  }

  public void deleteFile(Bucket bucket, String path) {
    StorageOperations ops = storageOperationsFactory.forBucket(bucket);
    ops.deleteFile(path);
  }

  public InputStream getInputStream(Bucket bucket, String path) {
    StorageOperations ops = storageOperationsFactory.forBucket(bucket);
    return ops.getInputStream(path);
  }

  // ==================== 内部方法 ====================

  /**
   * 删除 Bucket 底层容器及其所有内容。
   * 提取为独立方法，便于未来替换为异步实现（如 BucketDeleteJob）。
   */
  private void deleteBucketContents(Bucket bucket) {
    String prefix = buildPrefix(bucket);
    storageProvider.getBackend().deleteContainer(prefix);
  }

  private String buildPrefix(Bucket bucket) {
    return bucket.getOwnerType() + "/" + bucket.getOwnerId() + "/" + bucket.getName();
  }
}
