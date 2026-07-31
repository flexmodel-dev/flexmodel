package dev.flexmodel.flow.repository;

import dev.flexmodel.common.AbstractRepository;
import jakarta.enterprise.context.ApplicationScoped;
import dev.flexmodel.codegen.entity.NodeInstanceLog;
import dev.flexmodel.session.Session;

import java.util.List;

@ApplicationScoped
public class NodeInstanceLogFmRepository extends AbstractRepository implements NodeInstanceLogRepository {

  @Override
  public boolean insertList(String projectId, List<NodeInstanceLog> nodeInstanceLogList) {
    try (Session session = getProjectSession(projectId)) {
      boolean ok = true;
      for (NodeInstanceLog log : nodeInstanceLogList) {
        int r = session.dsl().insertInto(NodeInstanceLog.class).values(log).execute();
        ok &= r > 0;
      }
      return ok;
    }
  }
}
