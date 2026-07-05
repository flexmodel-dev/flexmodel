package dev.flexmodel.flow.repository;

import dev.flexmodel.codegen.System;
import dev.flexmodel.codegen.entity.InstanceData;
import dev.flexmodel.common.AbstractRepository;
import dev.flexmodel.session.Session;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class InstanceDataFmRepository extends AbstractRepository implements InstanceDataRepository {

  @Override
  public InstanceData select(String projectId, String flowInstanceId, String instanceDataId) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl().selectFrom(InstanceData.class)
        .where(System.instanceData.flowInstanceId.eq(flowInstanceId)
          .and(System.instanceData.instanceDataId.eq(instanceDataId)))
        .executeOne();
    }
  }

  @Override
  public InstanceData selectRecentOne(String projectId, String flowInstanceId) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl().selectFrom(InstanceData.class)
        .where(System.instanceData.flowInstanceId.eq(flowInstanceId))
        .orderByDesc(System.instanceData.id)
        .limit(1)
        .executeOne();
    }
  }

  @Override
  public int insert(InstanceData instanceData) {
    try (Session session = sessionFactory.createSession()) {
      return session.dsl().insertInto(InstanceData.class).values(instanceData).execute();
    }
  }

  @Override
  public int updateData(String projectId, InstanceData instanceData) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl().update(InstanceData.class)
        .values(instanceData)
        .where(System.instanceData.id.eq(instanceData.getId()))
        .execute();
    }
  }

  @Override
  public int insertOrUpdate(String projectId, InstanceData mergeEntity) {
    if (mergeEntity.getId() != null) {
      return updateData(projectId, mergeEntity);
    }
    return insert(mergeEntity);
  }
}
