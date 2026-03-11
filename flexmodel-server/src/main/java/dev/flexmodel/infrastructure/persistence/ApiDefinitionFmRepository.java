package dev.flexmodel.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import dev.flexmodel.codegen.entity.ApiDefinition;
import dev.flexmodel.domain.model.api.ApiDefinitionRepository;
import dev.flexmodel.session.Session;

import java.util.List;

import static dev.flexmodel.query.Expressions.field;

@ApplicationScoped
public class ApiDefinitionFmRepository extends AbstractRepository implements ApiDefinitionRepository {

  @Override
  public void deleteByParentId(String projectId, String parentId) {
    try (Session session = getProjectSession(projectId)) {
      session.dsl()
        .deleteFrom(ApiDefinition.class)
        .where(field(ApiDefinition::getProjectId).eq(projectId).and(field(ApiDefinition::getParentId).eq(parentId)))
        .execute();
    }
  }

  @Override
  public ApiDefinition findById(String projectId, String id) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl()
        .selectFrom(ApiDefinition.class)
        .where(field(ApiDefinition::getProjectId).eq(projectId).and(field(ApiDefinition::getId).eq(id)))
        .executeOne();
    }
  }

  @Override
  public List<ApiDefinition> findAll(String projectId) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl()
        .selectFrom(ApiDefinition.class)
        .where(field(ApiDefinition::getProjectId).eq(projectId))
        .execute();
    }
  }

  @Override
  public List<ApiDefinition> findByProjectId(String projectId) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl()
        .selectFrom(ApiDefinition.class)
        .where(field(ApiDefinition::getProjectId).eq(projectId))
        .execute();
    }
  }

  @Override
  public ApiDefinition save(ApiDefinition record) {
    try (Session session = getProjectSession(record.getProjectId())) {
      session.dsl().mergeInto(ApiDefinition.class).values(record).execute();
    }
    return record;
  }

  @Override
  public void delete(String projectId, String id) {
    try (Session session = getProjectSession(projectId)) {
      session.dsl().deleteFrom(ApiDefinition.class)
        .where(field(ApiDefinition::getProjectId).eq(projectId).and(field(ApiDefinition::getId).eq(id)))
        .execute();
    }
  }

  @Override
  public Integer count(String projectId) {
    try (Session session = getProjectSession(projectId)) {
      return (int) session.dsl().selectFrom(ApiDefinition.class)
        .where(field(ApiDefinition::getProjectId).eq(projectId))
        .count();
    }
  }

}
