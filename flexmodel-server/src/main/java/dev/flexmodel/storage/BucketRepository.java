package dev.flexmodel.storage;

import dev.flexmodel.codegen.entity.Bucket;

import java.util.List;
import java.util.Optional;

/**
 * Bucket 仓储接口
 *
 * @author cjbi
 */
public interface BucketRepository {

  List<Bucket> findByOwner(String ownerType, String ownerId);

  Optional<Bucket> findOne(String ownerType, String ownerId, String bucketName);

  Bucket save(Bucket bucket);

  void delete(String ownerType, String ownerId, String bucketName);

  Integer count(String ownerType, String ownerId);
}
