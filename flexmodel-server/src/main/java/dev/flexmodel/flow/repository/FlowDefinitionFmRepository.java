package dev.flexmodel.flow.repository;

import dev.flexmodel.codegen.entity.FlowDefinition;
import dev.flexmodel.common.AbstractRepository;
import dev.flexmodel.query.Predicate;
import dev.flexmodel.session.Session;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

import static dev.flexmodel.codegen.System.flowDefinition;

@ApplicationScoped
public class FlowDefinitionFmRepository extends AbstractRepository implements FlowDefinitionRepository {

  @Override
  public int insert(String projectId, FlowDefinition flowDefinition) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl().insertInto(FlowDefinition.class).values(flowDefinition).execute();
    }
  }

  @Override
  public int updateByModuleId(String projectId, FlowDefinition record) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl()
        .update(FlowDefinition.class)
        .values(record)
        .where(flowDefinition.flowModuleId.eq(record.getFlowModuleId()))
        .execute();
    }
  }

  @Override
  public FlowDefinition selectByModuleId(String projectId, String flowModuleId) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl()
        .selectFrom(FlowDefinition.class)
        .where(flowDefinition.flowModuleId.eq(flowModuleId))
        .executeOne();
    }
  }

  @Override
  public List<FlowDefinition> find(String projectId, Predicate filter, Integer page, Integer size) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl().selectFrom(FlowDefinition.class)
        .page(page, size)
        .where(filter)
        .orderByDesc(flowDefinition.id)
        .execute();
    }
  }

  @Override
  public long count(String projectId, Predicate filter) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl().selectFrom(FlowDefinition.class)
        .where(filter)
        .count();
    }
  }
}
