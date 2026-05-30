package dev.flexmodel.flow.repository;

import dev.flexmodel.common.AbstractRepository;
import jakarta.enterprise.context.ApplicationScoped;
import dev.flexmodel.codegen.entity.FlowInstanceMapping;
import dev.flexmodel.session.Session;

import java.util.List;

import static dev.flexmodel.query.Expressions.field;

@ApplicationScoped
public class FlowInstanceMappingFmRepository extends AbstractRepository implements FlowInstanceMappingRepository {

  @Override
  public List<FlowInstanceMapping> selectFlowInstanceMappingList(String projectId, String flowInstanceId, String nodeInstanceId) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl().selectFrom(FlowInstanceMapping.class)
        .where(field(FlowInstanceMapping::getProjectId).eq(projectId)
          .and(field(FlowInstanceMapping::getFlowInstanceId).eq(flowInstanceId))
          .and(field(FlowInstanceMapping::getNodeInstanceId).eq(nodeInstanceId)))
        .orderBy(FlowInstanceMapping::getCreateTime)
        .execute();
    }
  }

  @Override
  public FlowInstanceMapping selectFlowInstanceMapping(String projectId, String flowInstanceId, String nodeInstanceId) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl().selectFrom(FlowInstanceMapping.class)
        .where(field(FlowInstanceMapping::getProjectId).eq(projectId)
          .and(field(FlowInstanceMapping::getFlowInstanceId).eq(flowInstanceId))
          .and(field(FlowInstanceMapping::getNodeInstanceId).eq(nodeInstanceId)))
        .executeOne();
    }
  }

  @Override
  public int insert(FlowInstanceMapping flowInstanceMapping) {
    try (Session session = getProjectSession(flowInstanceMapping.getProjectId())) {
      return session.dsl().insertInto(FlowInstanceMapping.class).values(flowInstanceMapping).execute();
    }
  }

  @Override
  public void updateType(String projectId, String flowInstanceId, String nodeInstanceId, int type) {
    try (Session session = getProjectSession(projectId)) {
      session.dsl().update(FlowInstanceMapping.class)
        .set(FlowInstanceMapping::getType, type)
        .where(field(FlowInstanceMapping::getProjectId).eq(projectId)
          .and(field(FlowInstanceMapping::getFlowInstanceId).eq(flowInstanceId))
          .and(field(FlowInstanceMapping::getNodeInstanceId).eq(nodeInstanceId)))
        .execute();
    }
  }
}
