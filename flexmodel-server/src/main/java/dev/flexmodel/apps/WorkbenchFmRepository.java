package dev.flexmodel.apps;

import dev.flexmodel.codegen.entity.Workbench;
import dev.flexmodel.query.Direction;
import dev.flexmodel.session.Session;
import dev.flexmodel.session.SessionFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

import static dev.flexmodel.codegen.System.workbench;

@ApplicationScoped
public class WorkbenchFmRepository implements WorkbenchRepository {

  @Inject
  SessionFactory sessionFactory;

  @Override
  public List<Workbench> findAll() {
    try (Session session = sessionFactory.createSession()) {
      return session.dsl().selectFrom(Workbench.class)
        .orderBy(workbench.createdAt, Direction.DESC)
        .execute();
    }
  }

  @Override
  public List<Workbench> findByProjectId(String projectId) {
    try (Session session = sessionFactory.createSession()) {
      return session.dsl().selectFrom(Workbench.class)
        .where(workbench.projectId.eq(projectId))
        .orderBy(workbench.createdAt, Direction.DESC)
        .execute();
    }
  }

  @Override
  public Workbench findByProjectIdAndId(String projectId, String workbenchId) {
    try (Session session = sessionFactory.createSession()) {
      return session.dsl().selectFrom(Workbench.class)
        .where(workbench.projectId.eq(projectId).and(workbench.id.eq(workbenchId)))
        .executeOne();
    }
  }

  @Override
  public Workbench findByName(String projectId, String name) {
    try (Session session = sessionFactory.createSession()) {
      return session.dsl().selectFrom(Workbench.class)
        .where(workbench.projectId.eq(projectId).and(workbench.name.eq(name)))
        .executeOne();
    }
  }

  @Override
  public Workbench save(Workbench workbenchEntity) {
    try (Session session = sessionFactory.createSession()) {
      session.dsl().mergeInto(Workbench.class)
        .values(workbenchEntity)
        .execute();
    }
    return workbenchEntity;
  }

  @Override
  public long countByProjectId(String projectId) {
    try (Session session = sessionFactory.createSession()) {
      return session.dsl().selectFrom(Workbench.class)
        .where(workbench.projectId.eq(projectId))
        .count();
    }
  }

  @Override
  public void deleteByProjectIdAndId(String projectId, String workbenchId) {
    try (Session session = sessionFactory.createSession()) {
      session.dsl().deleteFrom(Workbench.class)
        .where(workbench.projectId.eq(projectId).and(workbench.id.eq(workbenchId)))
        .execute();
    }
  }

  @Override
  public void deleteByProjectId(String projectId) {
    try (Session session = sessionFactory.createSession()) {
      session.dsl().deleteFrom(Workbench.class)
        .where(workbench.projectId.eq(projectId))
        .execute();
    }
  }
}
