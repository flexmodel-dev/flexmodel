package dev.flexmodel.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import dev.flexmodel.codegen.entity.FlowDeployment;
import dev.flexmodel.domain.model.flow.repository.FlowDeploymentRepository;
import dev.flexmodel.query.Predicate;
import dev.flexmodel.session.Session;

import static dev.flexmodel.query.Expressions.field;

@ApplicationScoped
public class FlowDeploymentFmRepository extends AbstractRepository implements FlowDeploymentRepository {

  @Override
  public int insert(FlowDeployment flowDeployment) {
    try (Session session = getProjectSession(flowDeployment.getProjectId())) {
      return session.dsl().insertInto(FlowDeployment.class).values(flowDeployment).execute();
    }
  }

  @Override
  public FlowDeployment findByDeployId(String projectId, String flowDeployId) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl()
        .selectFrom(FlowDeployment.class)
        .where(field(FlowDeployment::getProjectId).eq(projectId).and(field(FlowDeployment::getFlowDeployId).eq(flowDeployId)))
        .executeOne();
    }
  }

  @Override
  public FlowDeployment findRecentByFlowModuleId(String projectId, String flowModuleId) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl()
        .selectFrom(FlowDeployment.class)
        .where(field(FlowDeployment::getProjectId).eq(projectId).and(field(FlowDeployment::getFlowModuleId).eq(flowModuleId)))
        .orderByDesc(FlowDeployment::getId)
        .limit(1)
        .executeOne();
    }
  }

  @Override
  public void deleteById(String projectId, Long id) {
    try (Session session = getProjectSession(projectId)) {
      session.dsl().deleteFrom(FlowDeployment.class)
        .where(field(FlowDeployment::getProjectId).eq(projectId).and(field(FlowDeployment::getId).eq(id)))
        .execute();
    }
  }

  @Override
  public long count(String projectId, Predicate filter) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl()
        .selectFrom(FlowDeployment.class)
        .where(field(FlowDeployment::getProjectId).eq(projectId).and(filter))
        .count();
    }
  }

}
