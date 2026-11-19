package dev.flexmodel.flow.repository;

import dev.flexmodel.common.AbstractRepository;
import dev.flexmodel.query.Direction;
import jakarta.enterprise.context.ApplicationScoped;
import dev.flexmodel.codegen.entity.NodeInstanceLog;
import dev.flexmodel.session.Session;

import java.time.LocalDateTime;
import java.util.List;

import static dev.flexmodel.codegen.System.nodeInstanceLog;

@ApplicationScoped
public class NodeInstanceLogFmRepository extends AbstractRepository implements NodeInstanceLogRepository {

  @Override
  public boolean insertList(String projectId, List<NodeInstanceLog> nodeInstanceLogList) {
    try (Session session = openSession()) {
      boolean ok = true;
      for (NodeInstanceLog log : nodeInstanceLogList) {
        log.setProjectId(projectId);
        int r = session.dsl().insertInto(NodeInstanceLog.class).values(log).execute();
        ok &= r > 0;
      }
      return ok;
    }
  }

  @Override
  public List<NodeInstanceLog> findByTraceId(String projectId, String traceId, int limit) {
    if (traceId == null || traceId.isEmpty()) {
      return List.of();
    }
    try (Session session = openSession()) {
      return session.dsl()
        .selectFrom(NodeInstanceLog.class)
        .where(nodeInstanceLog.projectId.eq(projectId).and(nodeInstanceLog.traceId.eq(traceId)))
        .orderBy(nodeInstanceLog.id, Direction.ASC)
        .page(1, limit)
        .execute();
    }
  }

  @Override
  public void purgeOldLogs(String projectId, int maxDays) {
    LocalDateTime purgeDate = LocalDateTime.now().minusDays(maxDays);
    try (Session session = openSession()) {
      session.dsl()
        .deleteFrom(NodeInstanceLog.class)
        .where(nodeInstanceLog.projectId.eq(projectId).and(nodeInstanceLog.createdAt.lte(purgeDate)))
        .execute();
    }
  }
}
