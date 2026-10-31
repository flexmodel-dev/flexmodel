package dev.flexmodel.apps.app;

import dev.flexmodel.codegen.entity.App;
import dev.flexmodel.session.Session;
import dev.flexmodel.session.SessionFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

import static dev.flexmodel.codegen.System.app;

@ApplicationScoped
public class AppFmRepository implements AppRepository {

  @Inject
  SessionFactory sessionFactory;

  @Override
  public List<App> findApps() {
    try (Session session = sessionFactory.createSession()) {
      return session.dsl().selectFrom(App.class)
        .orderByDesc(app.createdAt)
        .execute();
    }
  }

  @Override
  public App findApp(String appId) {
    try (Session session = sessionFactory.createSession()) {
      return session.dsl().selectFrom(App.class)
        .where(app.id.eq(appId))
        .executeOne();
    }
  }

  @Override
  public App save(App app) {
    try (Session session = sessionFactory.createSession()) {
      session.dsl().mergeInto(App.class)
        .values(app)
        .execute();
    }
    return app;
  }

  @Override
  public void delete(String appId) {
    try (Session session = sessionFactory.createSession()) {
      session.dsl().deleteFrom(App.class)
        .where(app.id.eq(appId))
        .execute();
    }
  }
}
