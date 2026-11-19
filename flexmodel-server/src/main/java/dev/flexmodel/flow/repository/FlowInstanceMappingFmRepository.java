package dev.flexmodel.flow.repository;

import dev.flexmodel.codegen.entity.FlowInstanceMapping;
import dev.flexmodel.common.AbstractRepository;
import dev.flexmodel.session.Session;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

import static dev.flexmodel.codegen.System.flowInstanceMapping;

@ApplicationScoped
public class FlowInstanceMappingFmRepository extends AbstractRepository implements FlowInstanceMappingRepository {

  @Override
  public List<FlowInstanceMapping> selectFlowInstanceMappingList(String projectId, String flowInstanceId, String nodeInstanceId) {
    try (Session session = openSession()) {
      return session.dsl().selectFrom(FlowInstanceMapping.class)
        .where(flowInstanceMapping.projectId.eq(projectId)
          .and(flowInstanceMapping.flowInstanceId.eq(flowInstanceId))
          .and(flowInstanceMapping.nodeInstanceId.eq(nodeInstanceId)))
        .orderBy(flowInstanceMapping.createdAt)
        .execute();
    }
  }

  @Override
  public FlowInstanceMapping selectFlowInstanceMapping(String projectId, String flowInstanceId, String nodeInstanceId) {
    try (Session session = openSession()) {
      return session.dsl().selectFrom(FlowInstanceMapping.class)
        .where(flowInstanceMapping.projectId.eq(projectId)
          .and(flowInstanceMapping.flowInstanceId.eq(flowInstanceId))
          .and(flowInstanceMapping.nodeInstanceId.eq(nodeInstanceId)))
        .executeOne();
    }
  }

  @Override
  public int insert(String projectId, FlowInstanceMapping flowInstanceMapping) {
    flowInstanceMapping.setProjectId(projectId);
    try (Session session = openSession()) {
      return session.dsl().insertInto(FlowInstanceMapping.class).values(flowInstanceMapping).execute();
    }
  }

  @Override
  public void updateType(String projectId, String flowInstanceId, String nodeInstanceId, int type) {
    try (Session session = openSession()) {
      session.dsl().update(FlowInstanceMapping.class)
        .set(flowInstanceMapping.type, type)
        .where(flowInstanceMapping.projectId.eq(projectId)
          .and(flowInstanceMapping.flowInstanceId.eq(flowInstanceId))
          .and(flowInstanceMapping.nodeInstanceId.eq(nodeInstanceId)))
        .execute();
    }
  }
}
