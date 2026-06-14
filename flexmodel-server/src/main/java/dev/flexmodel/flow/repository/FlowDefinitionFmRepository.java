package dev.flexmodel.flow.repository;

import dev.flexmodel.common.AbstractRepository;
import jakarta.enterprise.context.ApplicationScoped;
import dev.flexmodel.codegen.entity.FlowDefinition;
import dev.flexmodel.query.Predicate;
import dev.flexmodel.session.Session;

import java.util.List;

import static dev.flexmodel.query.Expressions.field;

@ApplicationScoped
public class FlowDefinitionFmRepository extends AbstractRepository implements FlowDefinitionRepository {

  @Override
  public int insert(String projectId, FlowDefinition flowDefinition) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl().insertInto(FlowDefinition.class).values(flowDefinition).execute();
    }
  }

  @Override
  public int updateByModuleId(String projectId, FlowDefinition flowDefinition) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl()
        .update(FlowDefinition.class)
        .values(flowDefinition)
        .where(field(FlowDefinition::getFlowModuleId).eq(flowDefinition.getFlowModuleId()))
        .execute();
    }
  }

  @Override
  public FlowDefinition selectByModuleId(String projectId, String flowModuleId) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl()
        .selectFrom(FlowDefinition.class)
        .where(field(FlowDefinition::getFlowModuleId).eq(flowModuleId))
        .executeOne();
    }
  }

  @Override
  public List<FlowDefinition> find(String projectId, Predicate filter, Integer page, Integer size) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl().selectFrom(FlowDefinition.class)
        .page(page, size)
        .where(filter)
        .orderByDesc("id")
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
