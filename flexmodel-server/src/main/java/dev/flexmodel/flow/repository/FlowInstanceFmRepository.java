package dev.flexmodel.flow.repository;

import dev.flexmodel.codegen.System;
import dev.flexmodel.codegen.entity.FlowInstance;
import dev.flexmodel.common.AbstractRepository;
import dev.flexmodel.query.Predicate;
import dev.flexmodel.session.Session;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class FlowInstanceFmRepository extends AbstractRepository implements FlowInstanceRepository {

  @Override
  public FlowInstance selectByFlowInstanceId(String projectId, String flowInstanceId) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl()
        .selectFrom(FlowInstance.class)
        .where(System.flowInstance.flowInstanceId.eq(flowInstanceId))
        .executeOne();
    }
  }

  @Override
  public int insert(String projectId, FlowInstance flowInstance) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl().insertInto(FlowInstance.class).values(flowInstance).execute();
    }
  }

  @Override
  public void updateStatus(String projectId, String flowInstanceId, int status) {
    try (Session session = getProjectSession(projectId)) {
      session.dsl()
        .update(FlowInstance.class)
        .set("status", status)
        .where(System.flowInstance.flowInstanceId.eq(flowInstanceId))
        .execute();
    }
  }

  @Override
  public void updateStatus(String projectId, FlowInstance flowInstance, int status) {
    try (Session session = getProjectSession(projectId)) {
      session.dsl()
        .update(FlowInstance.class)
        .set("status", status)
        .where(System.flowInstance.flowInstanceId.eq(flowInstance.getFlowInstanceId()))
        .execute();
    }
  }

  @Override
  public long count(String projectId, Predicate predicate) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl()
        .selectFrom(FlowInstance.class)
        .where(predicate)
        .count();
    }
  }

  @Override
  public List<FlowInstance> find(String projectId, Predicate predicate, Integer page, Integer size) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl()
        .selectFrom(FlowInstance.class)
        .where(predicate)
        .page(page, size)
        .orderByDesc("createTime")
        .execute();
    }
  }

}
