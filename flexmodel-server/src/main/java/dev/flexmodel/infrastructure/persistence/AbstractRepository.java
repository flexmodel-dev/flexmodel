package dev.flexmodel.infrastructure.persistence;

import dev.flexmodel.session.Session;
import dev.flexmodel.session.SessionFactory;
import dev.flexmodel.shared.SessionContextHolder;
import jakarta.inject.Inject;

/**
 * @author chengjinbao
 */
public abstract class AbstractRepository {

  @Inject
  SessionFactory sessionFactory;

  protected Session getProjectSession(String projectId) {
    String projectDatabaseName = SessionContextHolder.getProjectDatabaseName();
    if (projectDatabaseName == null) {
      return sessionFactory.createSession();
    }
    return sessionFactory.createSession(projectDatabaseName);
  }

}
