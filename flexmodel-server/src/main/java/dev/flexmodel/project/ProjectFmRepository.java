package dev.flexmodel.project;

import dev.flexmodel.codegen.entity.Project;
import dev.flexmodel.session.Session;
import dev.flexmodel.session.SessionFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

import static dev.flexmodel.codegen.System.project;

/**
 * 租户
 *
 * @author cjbi
 */
@ApplicationScoped
public class ProjectFmRepository implements ProjectRepository {

  @Inject
  SessionFactory sessionFactory;

  @Override
  public List<Project> findProjects() {
    try (Session session = sessionFactory.createSession()) {
      return session.dsl().selectFrom(Project.class)
        .where(project.enabled.eq(true)).orderByDesc(project.createdAt).execute();
    }
  }

  @Override
  public List<Project> findTopLevelProjects() {
    return findProjects().stream()
      .filter(p -> p.getParentProjectId() == null)
      .toList();
  }

  @Override
  public List<Project> findBranchProjects(String parentProjectId) {
    return findProjects().stream()
      .filter(p -> parentProjectId.equals(p.getParentProjectId()))
      .toList();
  }

  @Override
  public Project findProject(String projectId) {
    try (Session session = sessionFactory.createSession()) {
      return session.dsl().selectFrom(Project.class)
        .where(project.id.eq(projectId))
          .executeOne();
    }
  }

  @Override
  public Project save(Project project) {
    try (Session session = sessionFactory.createSession()) {
      session.dsl().mergeInto(Project.class)
          .values(project).execute();
    }
    return project;
  }

  @Override
  public void delete(String projectId) {
    try (Session session = sessionFactory.createSession()) {
      session.dsl().deleteFrom(Project.class)
        .where(project.id.eq(projectId))
          .execute();
    }
  }

  @Override
  public Project findProjectByDatabaseName(String databaseName) {
    try (Session session = sessionFactory.createSession()) {
      return session.dsl().selectFrom(Project.class)
        .where(project.databaseName.eq(databaseName))
        .executeOne();
    }
  }
}
