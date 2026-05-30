package dev.flexmodel.flow.repository;

import dev.flexmodel.common.AbstractRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import dev.flexmodel.codegen.entity.NodeInstance;
import dev.flexmodel.flow.common.NodeInstanceStatus;
import dev.flexmodel.session.Session;

import java.util.List;

import static dev.flexmodel.query.Expressions.field;

@ApplicationScoped
public class NodeInstanceFmRepository extends AbstractRepository implements NodeInstanceRepository {

  @Inject
  Session session;

  @Override
  public boolean insertOrUpdateList(List<NodeInstance> nodeInstanceList) {
    boolean ok = true;
    for (NodeInstance ni : nodeInstanceList) {
      if (ni.getId() == null) {
        int r = session.dsl().insertInto(NodeInstance.class).values(ni).execute();
        ok = ok && r > 0;
      } else {
        int r = session.dsl()
          .update(NodeInstance.class)
          .set(NodeInstance::getStatus, ni.getStatus())
          .set(NodeInstance::getModifyTime, ni.getModifyTime())
          .where(field(NodeInstance::getId).eq(ni.getId()))
          .execute();
        ok = ok && r > 0;
      }
    }
    return ok;
  }

  @Override
  public NodeInstance selectByNodeInstanceId(String projectId, String flowInstanceId, String nodeInstanceId) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl().selectFrom(NodeInstance.class)
        .where(field(NodeInstance::getProjectId).eq(projectId).and(field(NodeInstance::getFlowInstanceId).eq(flowInstanceId)
          .and(field(NodeInstance::getNodeInstanceId).eq(nodeInstanceId))))
        .executeOne();
    }
  }

  @Override
  public NodeInstance selectBySourceInstanceId(String projectId, String flowInstanceId, String sourceNodeInstanceId, String nodeKey) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl().selectFrom(NodeInstance.class)
        .where(field(NodeInstance::getProjectId).eq(projectId).and(field(NodeInstance::getFlowInstanceId).eq(flowInstanceId)
          .and(field(NodeInstance::getSourceNodeInstanceId).eq(sourceNodeInstanceId))
          .and(field(NodeInstance::getNodeKey).eq(nodeKey))))
        .executeOne();
    }
  }

  @Override
  public NodeInstance selectRecentOne(String projectId, String flowInstanceId) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl().selectFrom(NodeInstance.class)
        .where(field(NodeInstance::getProjectId).eq(projectId).and(field(NodeInstance::getFlowInstanceId).eq(flowInstanceId)))
        .orderByDesc(NodeInstance::getId)
        .limit(1)
        .executeOne();
    }
  }

  @Override
  public NodeInstance selectRecentActiveOne(String projectId, String flowInstanceId) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl().selectFrom(NodeInstance.class)
        .where(field(NodeInstance::getProjectId).eq(projectId).and(field(NodeInstance::getFlowInstanceId).eq(flowInstanceId)
          .and(field(NodeInstance::getStatus).eq(NodeInstanceStatus.ACTIVE))))
        .orderByDesc(NodeInstance::getId)
        .limit(1)
        .executeOne();
    }
  }

  @Override
  public NodeInstance selectRecentCompletedOne(String projectId, String flowInstanceId) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl().selectFrom(NodeInstance.class)
        .where(field(NodeInstance::getProjectId).eq(projectId).and(field(NodeInstance::getFlowInstanceId).eq(flowInstanceId)
          .and(field(NodeInstance::getStatus).eq(NodeInstanceStatus.COMPLETED))))
        .orderByDesc(NodeInstance::getId)
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
        .where(field(NodeInstance::getProjectId).eq(projectId).and(field(NodeInstance::getFlowInstanceId).eq(flowInstanceId)))
        .orderBy(NodeInstance::getId)
        .execute();
    }
  }

  @Override
  public List<NodeInstance> selectDescByFlowInstanceId(String projectId, String flowInstanceId) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl().selectFrom(NodeInstance.class)
        .where(field(NodeInstance::getProjectId).eq(projectId).and(field(NodeInstance::getFlowInstanceId).eq(flowInstanceId)))
        .orderByDesc(NodeInstance::getId)
        .execute();
    }
  }

  @Override
  public void updateStatus(String projectId, NodeInstance nodeInstance, int status) {
    try (Session session = getProjectSession(projectId)) {
      session.dsl().update(NodeInstance.class)
        .set(NodeInstance::getStatus, status)
        .where(field(NodeInstance::getProjectId).eq(projectId).and(field(NodeInstance::getId).eq(nodeInstance.getId())))
        .execute();
    }
  }

  @Override
  public List<NodeInstance> selectByFlowInstanceIdAndNodeKey(String projectId, String flowInstanceId, String nodeKey) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl().selectFrom(NodeInstance.class)
        .where(field(NodeInstance::getProjectId).eq(projectId).and(field(NodeInstance::getFlowInstanceId).eq(flowInstanceId)
          .and(field(NodeInstance::getNodeKey).eq(nodeKey))))
        .execute();
    }
  }
}
