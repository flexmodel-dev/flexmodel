package dev.flexmodel.flow.repository;

import dev.flexmodel.codegen.System;
import dev.flexmodel.codegen.entity.NodeInstance;
import dev.flexmodel.common.AbstractRepository;
import dev.flexmodel.flow.common.NodeInstanceStatus;
import dev.flexmodel.session.Session;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class NodeInstanceFmRepository extends AbstractRepository implements NodeInstanceRepository {

  @Override
  public boolean insertOrUpdateList(List<NodeInstance> nodeInstanceList) {
    try (Session session = sessionFactory.createSession()) {
      boolean ok = true;
      for (NodeInstance ni : nodeInstanceList) {
        if (ni.getId() == null) {
          int r = session.dsl().insertInto(NodeInstance.class).values(ni).execute();
          ok = ok && r > 0;
        } else {
          int r = session.dsl()
            .update(NodeInstance.class)
            .set("status", ni.getStatus())
            .set("modifyTime", ni.getModifyTime())
            .where(System.nodeInstance.id.eq(ni.getId()))
            .execute();
          ok = ok && r > 0;
        }
      }
      return ok;
    }
  }

  @Override
  public NodeInstance selectByNodeInstanceId(String projectId, String flowInstanceId, String nodeInstanceId) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl().selectFrom(NodeInstance.class)
        .where(System.nodeInstance.flowInstanceId.eq(flowInstanceId)
          .and(System.nodeInstance.nodeInstanceId.eq(nodeInstanceId)))
        .executeOne();
    }
  }

  @Override
  public NodeInstance selectBySourceInstanceId(String projectId, String flowInstanceId, String sourceNodeInstanceId, String nodeKey) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl().selectFrom(NodeInstance.class)
        .where(System.nodeInstance.flowInstanceId.eq(flowInstanceId)
          .and(System.nodeInstance.sourceNodeInstanceId.eq(sourceNodeInstanceId))
          .and(System.nodeInstance.nodeKey.eq(nodeKey)))
        .executeOne();
    }
  }

  @Override
  public NodeInstance selectRecentOne(String projectId, String flowInstanceId) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl().selectFrom(NodeInstance.class)
        .where(System.nodeInstance.flowInstanceId.eq(flowInstanceId))
        .orderByDesc("id")
        .limit(1)
        .executeOne();
    }
  }

  @Override
  public NodeInstance selectRecentActiveOne(String projectId, String flowInstanceId) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl().selectFrom(NodeInstance.class)
        .where(System.nodeInstance.flowInstanceId.eq(flowInstanceId)
          .and(System.nodeInstance.status.eq(NodeInstanceStatus.ACTIVE)))
        .orderByDesc("id")
        .limit(1)
        .executeOne();
    }
  }

  @Override
  public NodeInstance selectRecentCompletedOne(String projectId, String flowInstanceId) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl().selectFrom(NodeInstance.class)
        .where(System.nodeInstance.flowInstanceId.eq(flowInstanceId)
          .and(System.nodeInstance.status.eq(NodeInstanceStatus.COMPLETED)))
        .orderByDesc("id")
        .limit(1)
        .executeOne();
    }
  }

  @Override
  public NodeInstance selectEnabledOne(String projectId, String flowInstanceId) {
    NodeInstance active = selectRecentActiveOne(projectId, flowInstanceId);
    if (active != null) {
      return active;
    }
    return selectRecentCompletedOne(projectId, flowInstanceId);
  }

  @Override
  public List<NodeInstance> selectByFlowInstanceId(String projectId, String flowInstanceId) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl().selectFrom(NodeInstance.class)
        .where(System.nodeInstance.flowInstanceId.eq(flowInstanceId))
        .orderBy("id")
        .execute();
    }
  }

  @Override
  public List<NodeInstance> selectDescByFlowInstanceId(String projectId, String flowInstanceId) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl().selectFrom(NodeInstance.class)
        .where(System.nodeInstance.flowInstanceId.eq(flowInstanceId))
        .orderByDesc("id")
        .execute();
    }
  }

  @Override
  public void updateStatus(String projectId, NodeInstance nodeInstance, int status) {
    try (Session session = getProjectSession(projectId)) {
      session.dsl().update(NodeInstance.class)
        .set("status", status)
        .where(System.nodeInstance.id.eq(nodeInstance.getId()))
        .execute();
    }
  }

  @Override
  public List<NodeInstance> selectByFlowInstanceIdAndNodeKey(String projectId, String flowInstanceId, String nodeKey) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl().selectFrom(NodeInstance.class)
        .where(System.nodeInstance.flowInstanceId.eq(flowInstanceId)
          .and(System.nodeInstance.nodeKey.eq(nodeKey)))
        .execute();
    }
  }
}
