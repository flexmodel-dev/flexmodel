package dev.flexmodel.flow.repository;

import dev.flexmodel.codegen.entity.FlowDeployment;
import dev.flexmodel.common.AbstractRepository;
import dev.flexmodel.query.Predicate;
import dev.flexmodel.session.Session;
import jakarta.enterprise.context.ApplicationScoped;

import static dev.flexmodel.codegen.System.flowDeployment;

@ApplicationScoped
public class FlowDeploymentFmRepository extends AbstractRepository implements FlowDeploymentRepository {

  @Override
  public int insert(String projectId, FlowDeployment flowDeployment) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl().insertInto(FlowDeployment.class).values(flowDeployment).execute();
    }
  }

  @Override
  public FlowDeployment findByDeployId(String projectId, String flowDeployId) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl()
        .selectFrom(FlowDeployment.class)
        .where(flowDeployment.flowDeployId.eq(flowDeployId))
        .executeOne();
    }
  }

  @Override
  public FlowDeployment findRecentByFlowModuleId(String projectId, String flowModuleId) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl()
        .selectFrom(FlowDeployment.class)
        .where(flowDeployment.flowModuleId.eq(flowModuleId))
        .orderByDesc(flowDeployment.id)
        .limit(1)
        .executeOne();
    }
  }

  @Override
  public void deleteById(String projectId, Long id) {
    try (Session session = getProjectSession(projectId)) {
      session.dsl().deleteFrom(FlowDeployment.class)
        .where(flowDeployment.id.eq(id))
        .execute();
    }
  }

  @Override
  public long count(String projectId, Predicate filter) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl()
        .selectFrom(FlowDeployment.class)
        .where(filter)
        .count();
    }
  }

}
