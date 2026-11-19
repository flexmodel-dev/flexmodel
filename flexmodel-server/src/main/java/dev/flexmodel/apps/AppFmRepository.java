package dev.flexmodel.apps;

import dev.flexmodel.codegen.entity.App;
import dev.flexmodel.common.AbstractRepository;
import dev.flexmodel.query.Query;
import dev.flexmodel.session.Session;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

import static dev.flexmodel.codegen.System.app;

@ApplicationScoped
public class AppFmRepository extends AbstractRepository implements AppRepository {

  @Override
  public List<App> findApps() {
    try (Session session = openSession()) {
      // 链式 orderBy 每次整体替换排序，多级排序须聚合到同一个 OrderBy
      return session.dsl().selectFrom(App.class)
        .orderBy(new Query.OrderBy().asc("sort_order").desc("created_at"))
        .execute();
    }
  }

  @Override
  public App findApp(String appId) {
    try (Session session = openSession()) {
      return session.dsl().selectFrom(App.class)
        .where(app.id.eq(appId))
        .executeOne();
    }
  }

  @Override
  public App save(App app) {
    try (Session session = openSession()) {
      session.dsl().mergeInto(App.class)
        .values(app)
        .execute();
    }
    return app;
  }

  @Override
  public void reorder(List<String> appIds) {
    try (Session session = openSession()) {
      session.startTransaction();
      for (int i = 0; i < appIds.size(); i++) {
        session.dsl().update(App.class)
          .set(app.sortOrder, i)
          .where(app.id.eq(appIds.get(i)))
          .execute();
      }
      session.commit();
    }
  }

  @Override
  public void delete(String appId) {
    try (Session session = openSession()) {
      session.dsl().deleteFrom(App.class)
        .where(app.id.eq(appId))
        .execute();
    }
  }
}
