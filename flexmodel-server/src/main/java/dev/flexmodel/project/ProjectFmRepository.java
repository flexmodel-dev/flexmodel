package dev.flexmodel.project;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import dev.flexmodel.codegen.entity.Project;
import dev.flexmodel.query.Expressions;
import dev.flexmodel.session.Session;
import dev.flexmodel.session.SessionFactory;

import java.util.List;

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
      return session.dsl().selectFrom(Project.class).where(Expressions.field(Project::getEnabled).eq(true)).execute().stream()
        .toList();
    }
  }

  @Override
  public Project findProject(String projectId) {
    try (Session session = sessionFactory.createSession()) {
      return session.dsl().selectFrom(Project.class)
          .where(Expressions.field(Project::getId).eq(projectId))
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
          .where(Expressions.field(Project::getId).eq(projectId))
          .execute();
    }
  }
}
