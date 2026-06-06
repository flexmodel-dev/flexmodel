package dev.flexmodel.common;

import dev.flexmodel.codegen.entity.Branch;
import dev.flexmodel.codegen.entity.Project;
import dev.flexmodel.session.Session;
import dev.flexmodel.session.SessionFactory;
import jakarta.inject.Inject;

import static dev.flexmodel.query.Expressions.field;

/**
 * @author chengjinbao
 */
public abstract class AbstractRepository {

  @Inject
  SessionFactory sessionFactory;

  protected Session getProjectSession(String projectId) {
    String projectDatabaseName = SessionContextHolder.getProjectDatabaseName();
    if (projectDatabaseName != null) {
      return sessionFactory.createSession(projectDatabaseName);
    }
    if (projectId != null) {
      try (Session session = sessionFactory.createSession()) {
        Project project = session.dsl().selectFrom(Project.class)
          .where(field(Project::getId).eq(projectId))
          .executeOne();
        SessionContextHolder.setProjectId(projectId);
        SessionContextHolder.setProjectDatabaseName(project.getCurrentDatabaseName());
        return sessionFactory.createSession(project.getCurrentDatabaseName());
      }

    }
    return sessionFactory.createSession();
  }
}
