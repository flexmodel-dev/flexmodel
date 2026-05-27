package dev.flexmodel.api;

import dev.flexmodel.common.AbstractRepository;
import jakarta.enterprise.context.ApplicationScoped;
import dev.flexmodel.codegen.entity.ApiDefinitionHistory;
import dev.flexmodel.session.Session;

import java.util.List;

import static dev.flexmodel.query.Expressions.field;

@ApplicationScoped
public class ApiDefinitionHistoryFmRepository extends AbstractRepository implements ApiDefinitionHistoryRepository {

  @Override
  public List<ApiDefinitionHistory> findByApiDefinitionId(String projectId, String apiDefinitionId) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl().selectFrom(ApiDefinitionHistory.class)
        .where(field(ApiDefinitionHistory::getProjectId).eq(projectId).and(field(ApiDefinitionHistory::getApiDefinitionId).eq(apiDefinitionId)))
        .orderByDesc(ApiDefinitionHistory::getCreatedAt)
        .execute();
    }
  }

  @Override
  public ApiDefinitionHistory save(String projectId, ApiDefinitionHistory apiDefinitionHistory) {
    try (Session session = getProjectSession(projectId)) {
      session.dsl().insertInto(ApiDefinitionHistory.class)
        .values(apiDefinitionHistory)
        .execute();
    }
    return apiDefinitionHistory;
  }

  @Override
  public ApiDefinitionHistory findById(String projectId, String historyId) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl().selectFrom(ApiDefinitionHistory.class)
        .where(field(ApiDefinitionHistory::getProjectId).eq(projectId).and(field(ApiDefinitionHistory::getId).eq(historyId)))
        .executeOne();
    }
  }
}
