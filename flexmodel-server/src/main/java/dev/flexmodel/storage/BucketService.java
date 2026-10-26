package dev.flexmodel.storage;

import dev.flexmodel.codegen.entity.Bucket;
import dev.flexmodel.common.NotFoundException;
import dev.flexmodel.common.SessionContext;
import dev.flexmodel.common.ValidationException;
import dev.flexmodel.storage.config.StorageProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.InputStream;
import java.time.LocalDateTime;
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

  @Inject
  SessionContext sessionContext;

  // ==================== Bucket CRUD ====================

  public Bucket createBucket(String ownerType, String ownerId, Bucket bucket) {
    // 检查同名
    Optional<Bucket> existing = bucketRepository.findOne(ownerType, ownerId, bucket.getName());
    if (existing.isPresent()) {
      throw new ValidationException("Bucket name '" + bucket.getName() + "' already exists");
    }

    // 设置归属
    bucket.setOwnerType(ownerType);
    bucket.setOwnerId(ownerId);

    // 生成 ID
    if (bucket.getId() == null || bucket.getId().isEmpty()) {
      bucket.setId(UUID.randomUUID().toString());
    }
    // 审计字段：创建人 / 创建时间
    bucket.setCreatedBy(sessionContext.getUserId() != null ? sessionContext.getUserId() : "system");
    bucket.setCreatedAt(LocalDateTime.now());

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
      throw new NotFoundException("Bucket not found: " + bucketName)
;
    }
    Bucket old = existing.get();
    // 保留不可变字段
    bucket.setId(old.getId());
    bucket.setOwnerType(ownerType);
    bucket.setOwnerId(ownerId);
    bucket.setName(old.getName()); // name 不可修改
    bucket.setCreatedAt(old.getCreatedAt());
    bucket.setCreatedBy(old.getCreatedBy()); // 创建人不可变
    bucket.setUpdatedAt(LocalDateTime.now()); // 刷新更新时间
    bucket.setUpdatedBy(sessionContext.getUserId()); // 记录更新人
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

  public void moveFile(Bucket bucket, String source, String target) {
    String normalizedSource = normalizeObjectPath(source);
    String normalizedTarget = normalizeObjectPath(target);
    if (normalizedSource.isEmpty() || normalizedTarget.isEmpty()) {
      throw new ValidationException("源路径和目标路径不能为空");
    }
    if (normalizedSource.equals(normalizedTarget)) {
      throw new ValidationException("源路径和目标路径不能相同");
    }
    if (normalizedTarget.startsWith(normalizedSource + "/")) {
      throw new ValidationException("不能将文件移动到自身或其子目录");
    }

    StorageOperations ops = storageOperationsFactory.forBucket(bucket);
    if (ops.getFile(normalizedSource) == null) {
      throw new NotFoundException("File not found: " + source);
    }
    if (ops.getFile(normalizedTarget) != null) {
      throw new ValidationException("目标位置已存在同名文件或目录");
    }
    ops.moveFile(normalizedSource, normalizedTarget);
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

  private static String normalizeObjectPath(String path) {
    if (path == null) {
      return "";
    }
    String normalized = path.replace('\\', '/');
    while (normalized.startsWith("/")) {
      normalized = normalized.substring(1);
    }
    while (normalized.endsWith("/")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    return normalized;
  }
}
