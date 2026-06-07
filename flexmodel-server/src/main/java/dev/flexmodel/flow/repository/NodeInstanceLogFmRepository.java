package dev.flexmodel.flow.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import dev.flexmodel.codegen.entity.NodeInstanceLog;
import dev.flexmodel.session.Session;
import dev.flexmodel.session.SessionFactory;

import java.util.List;

@ApplicationScoped
public class NodeInstanceLogFmRepository implements NodeInstanceLogRepository {

  @Inject
  SessionFactory sessionFactory;

  @Override
  public boolean insertList(List<NodeInstanceLog> nodeInstanceLogList) {
    try (Session session = sessionFactory.createSession()) {
      boolean ok = true;
      for (NodeInstanceLog log : nodeInstanceLogList) {
        int r = session.dsl().insertInto(NodeInstanceLog.class).values(log).execute();
        ok &= r > 0;
      }
      return ok;
    }
  }
}


