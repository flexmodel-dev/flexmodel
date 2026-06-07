package dev.flexmodel.common;

import dev.flexmodel.codegen.entity.Branch;
import dev.flexmodel.codegen.entity.Project;
import dev.flexmodel.session.Session;
import dev.flexmodel.session.SessionFactory;
import jakarta.inject.Inject;

import dev.flexmodel.common.utils.StringUtils;

import static dev.flexmodel.query.Expressions.field;

/**
 * @author chengjinbao
 */
public abstract class AbstractRepository {

  @Inject
  protected SessionFactory sessionFactory;

  protected Session getProjectSession(String projectId) {
    if (projectId != null) {
      // 仅当缓存的 projectId 与请求的一致时才复用 databaseName
      String cachedProjectId = SessionContextHolder.getProjectId();
      if (projectId.equals(cachedProjectId)) {
        String cachedDb = SessionContextHolder.getProjectDatabaseName();
        if (cachedDb != null) {
          return sessionFactory.createSession(cachedDb);
        }
      }
      // 查询项目对应的 databaseName 并更新缓存
      try (Session session = sessionFactory.createSession()) {
        Project project = session.dsl().selectFrom(Project.class)
          .where(field(Project::getId).eq(projectId))
          .executeOne();
        if (project == null || project.getCurrentDatabaseName() == null) {
          throw new IllegalArgumentException("项目不存在或 databaseName 为空: " + projectId);
        }
        SessionContextHolder.setProjectId(projectId);
        SessionContextHolder.setProjectDatabaseName(project.getCurrentDatabaseName());
        return sessionFactory.createSession(project.getCurrentDatabaseName());
      }
    }
    String projectDatabaseName = SessionContextHolder.getProjectDatabaseName();
    if (projectDatabaseName != null) {
      return sessionFactory.createSession(projectDatabaseName);
    }
    return sessionFactory.createSession();
  }
}
