package dev.flexmodel.apps;

import dev.flexmodel.codegen.entity.AppWorkbench;
import dev.flexmodel.query.Direction;
import dev.flexmodel.session.Session;
import dev.flexmodel.session.SessionFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

import static dev.flexmodel.codegen.System.appWorkbench;

@ApplicationScoped
public class WorkbenchFmRepository implements WorkbenchRepository {

  @Inject
  SessionFactory sessionFactory;

  @Override
  public List<AppWorkbench> findAll() {
    try (Session session = sessionFactory.createSession()) {
      return session.dsl().selectFrom(AppWorkbench.class)
        .orderBy(appWorkbench.createdAt, Direction.DESC)
        .execute();
    }
  }

  @Override
  public List<AppWorkbench> findByProjectId(String projectId) {
    try (Session session = sessionFactory.createSession()) {
      return session.dsl().selectFrom(AppWorkbench.class)
        .where(appWorkbench.projectId.eq(projectId))
        .orderBy(appWorkbench.createdAt, Direction.DESC)
        .execute();
    }
  }

  @Override
  public AppWorkbench findByProjectIdAndId(String projectId, String workbenchId) {
    try (Session session = sessionFactory.createSession()) {
      return session.dsl().selectFrom(AppWorkbench.class)
        .where(appWorkbench.projectId.eq(projectId).and(appWorkbench.id.eq(workbenchId)))
        .executeOne();
    }
  }

  @Override
  public AppWorkbench findByName(String projectId, String name) {
    try (Session session = sessionFactory.createSession()) {
      return session.dsl().selectFrom(AppWorkbench.class)
        .where(appWorkbench.projectId.eq(projectId).and(appWorkbench.name.eq(name)))
        .executeOne();
    }
  }

  @Override
  public AppWorkbench save(AppWorkbench workbenchEntity) {
    try (Session session = sessionFactory.createSession()) {
      session.dsl().mergeInto(AppWorkbench.class)
        .values(workbenchEntity)
        .execute();
    }
    return workbenchEntity;
  }

  @Override
  public long countByProjectId(String projectId) {
    try (Session session = sessionFactory.createSession()) {
      return session.dsl().selectFrom(AppWorkbench.class)
        .where(appWorkbench.projectId.eq(projectId))
        .count();
    }
  }

  @Override
  public void deleteByProjectIdAndId(String projectId, String workbenchId) {
    try (Session session = sessionFactory.createSession()) {
      session.dsl().deleteFrom(AppWorkbench.class)
        .where(appWorkbench.projectId.eq(projectId).and(appWorkbench.id.eq(workbenchId)))
        .execute();
    }
  }

  @Override
  public void deleteByProjectId(String projectId) {
    try (Session session = sessionFactory.createSession()) {
      session.dsl().deleteFrom(AppWorkbench.class)
        .where(appWorkbench.projectId.eq(projectId))
        .execute();
    }
  }
}
