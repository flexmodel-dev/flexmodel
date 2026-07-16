package dev.flexmodel.common;

import dev.flexmodel.codegen.System;
import dev.flexmodel.codegen.entity.Project;
import dev.flexmodel.session.Session;
import dev.flexmodel.session.SessionFactory;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Inject;


/**
 * @author chengjinbao
 */
public abstract class AbstractRepository {

  @Inject
  protected SessionFactory sessionFactory;

  protected Session getProjectSession(String projectId) {
    SessionContext sessionContext = null;
    Project project = null;
    String cachedDb = null;
    String cachedProjectId = null;
    try {
      sessionContext = CDI.current().select(SessionContext.class).get();
      cachedDb = sessionContext.getProjectDatabaseName();
      cachedProjectId = sessionContext.getProjectId();
    } catch (Exception _) {
    }
    if (sessionContext != null) {
      if (projectId != null) {
        // 仅当缓存的 projectId 与请求的一致时才复用 databaseName

        if (projectId.equals(cachedProjectId)) {
          if (cachedDb != null) {
            return sessionFactory.createSession(cachedDb);
          }
        }
        // 查询项目对应的 databaseName 并更新缓存
        try (Session session = sessionFactory.createSession()) {
          project = session.dsl().selectFrom(Project.class)
            .where(System.project.id.eq(projectId))
            .executeOne();
          if (project == null || project.getDatabaseName() == null) {
            throw new IllegalArgumentException("项目不存在或 databaseName 为空: " + projectId);
          }
          try {
            sessionContext.setProjectId(projectId);
            sessionContext.setProjectDatabaseName(project.getDatabaseName());
          } catch (Exception _) {

          }
          return sessionFactory.createSession(project.getDatabaseName());
        }
      }
      return sessionFactory.createSession();
    }
    try (Session session = sessionFactory.createSession()) {
      project = session.dsl().selectFrom(Project.class)
        .where(System.project.id.eq(projectId))
        .executeOne();
      return sessionFactory.createSession(project.getDatabaseName());
    }
  }
}
