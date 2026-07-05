package dev.flexmodel.storage;

import dev.flexmodel.codegen.System;
import dev.flexmodel.codegen.entity.Bucket;
import dev.flexmodel.common.AbstractRepository;
import dev.flexmodel.session.Session;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

/**
 * Bucket 仓储 Flexmodel 实现
 *
 * @author cjbi
 */
@ApplicationScoped
public class BucketFmRepository extends AbstractRepository implements BucketRepository {

  @Override
  public List<Bucket> findByOwner(String ownerType, String ownerId) {
    try (Session session = getProjectSession(ownerId)) {
      return session.dsl()
        .selectFrom(Bucket.class)
        .where(System.bucket.ownerType.eq(ownerType)
          .and(System.bucket.ownerId.eq(ownerId)))
        .execute();
    }
  }

  @Override
  public Optional<Bucket> findOne(String ownerType, String ownerId, String bucketName) {
    try (Session session = getProjectSession(ownerId)) {
      return session.dsl()
        .selectFrom(Bucket.class)
        .where(System.bucket.ownerType.eq(ownerType)
          .and(System.bucket.ownerId.eq(ownerId))
          .and(System.bucket.name.eq(bucketName)))
        .execute()
        .stream()
        .findFirst();
    }
  }

  @Override
  public Bucket save(Bucket record) {
    try (Session session = getProjectSession(record.getOwnerId())) {
      session.dsl()
        .mergeInto(Bucket.class)
        .values(record)
        .execute();
    }
    return record;
  }

  @Override
  public void delete(String ownerType, String ownerId, String bucketName) {
    try (Session session = getProjectSession(ownerId)) {
      session.dsl().deleteFrom(Bucket.class)
        .where(System.bucket.ownerType.eq(ownerType)
          .and(System.bucket.ownerId.eq(ownerId))
          .and(System.bucket.name.eq(bucketName)))
        .execute();
    }
  }

  @Override
  public Integer count(String ownerType, String ownerId) {
    try (Session session = getProjectSession(ownerId)) {
      return (int) session.dsl().selectFrom(Bucket.class)
        .where(System.bucket.ownerType.eq(ownerType)
          .and(System.bucket.ownerId.eq(ownerId)))
        .count();
    }
  }
}
