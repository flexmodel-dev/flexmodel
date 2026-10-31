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
public class AppWorkbenchFmRepository implements AppWorkbenchRepository {

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
  public List<AppWorkbench> findByAppId(String appId) {
    try (Session session = sessionFactory.createSession()) {
      return session.dsl().selectFrom(AppWorkbench.class)
        .where(appWorkbench.appId.eq(appId))
        .orderBy(appWorkbench.createdAt, Direction.DESC)
        .execute();
    }
  }

  @Override
  public AppWorkbench findByAppIdAndId(String appId, String workbenchId) {
    try (Session session = sessionFactory.createSession()) {
      return session.dsl().selectFrom(AppWorkbench.class)
        .where(appWorkbench.appId.eq(appId).and(appWorkbench.id.eq(workbenchId)))
        .executeOne();
    }
  }

  @Override
  public AppWorkbench findByName(String appId, String name) {
    try (Session session = sessionFactory.createSession()) {
      return session.dsl().selectFrom(AppWorkbench.class)
        .where(appWorkbench.appId.eq(appId).and(appWorkbench.name.eq(name)))
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
  public long countByAppId(String appId) {
    try (Session session = sessionFactory.createSession()) {
      return session.dsl().selectFrom(AppWorkbench.class)
        .where(appWorkbench.appId.eq(appId))
        .count();
    }
  }

  @Override
  public void deleteByAppIdAndId(String appId, String workbenchId) {
    try (Session session = sessionFactory.createSession()) {
      session.dsl().deleteFrom(AppWorkbench.class)
        .where(appWorkbench.appId.eq(appId).and(appWorkbench.id.eq(workbenchId)))
        .execute();
    }
  }

  @Override
  public void deleteByAppId(String appId) {
    try (Session session = sessionFactory.createSession()) {
      session.dsl().deleteFrom(AppWorkbench.class)
        .where(appWorkbench.appId.eq(appId))
        .execute();
    }
  }
}
