package dev.flexmodel.storage;

import dev.flexmodel.common.AbstractRepository;
import jakarta.enterprise.context.ApplicationScoped;
import dev.flexmodel.codegen.entity.Storage;
import dev.flexmodel.query.Predicate;
import dev.flexmodel.session.Session;

import java.util.List;
import java.util.Optional;

import static dev.flexmodel.query.Expressions.field;

@ApplicationScoped
public class StorageFmRepository extends AbstractRepository implements StorageRepository {

  @Override
  public List<Storage> findAll(String projectId) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl()
        .selectFrom(Storage.class)
        .where(field(Storage::getProjectId).eq(projectId))
        .execute();
    }
  }

  @Override
  public List<Storage> find(String projectId, Predicate filter) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl()
        .selectFrom(Storage.class)
        .where(field(Storage::getProjectId).eq(projectId).and(filter))
        .execute();
    }
  }

  @Override
  public Optional<Storage> findOne(String projectId, String name) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl()
        .selectFrom(Storage.class)
        .where(field(Storage::getProjectId).eq(projectId).and(field(Storage::getName).eq(name)))
        .execute()
        .stream()
        .findFirst();
    }
  }

  @Override
  public Storage save(Storage record) {
    try (Session session = getProjectSession(record.getProjectId())) {
      session.dsl()
        .mergeInto(Storage.class)
        .values(record)
        .execute();
    }
    return record;
  }

  @Override
  public void delete(String projectId, String name) {
    try (Session session = getProjectSession(projectId)) {
      session.dsl().deleteFrom(Storage.class)
        .where(field(Storage::getProjectId).eq(projectId).and(field(Storage::getName).eq(name)))
        .execute();
    }
  }

  @Override
  public Integer count(String projectId) {
    try (Session session = getProjectSession(projectId)) {
      return (int) session.dsl().selectFrom(Storage.class)
        .where(field(Storage::getProjectId).eq(projectId))
        .count();
    }
  }
}
