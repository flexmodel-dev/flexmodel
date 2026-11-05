package dev.flexmodel.apps.form;

import dev.flexmodel.codegen.entity.AppForm;
import dev.flexmodel.query.Direction;
import dev.flexmodel.session.Session;
import dev.flexmodel.session.SessionFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

import static dev.flexmodel.codegen.System.appForm;

@ApplicationScoped
public class AppFormFmRepository implements AppFormRepository {

  @Inject
  SessionFactory sessionFactory;

  @Override
  public List<AppForm> findByAppId(String appId) {
    try (Session session = sessionFactory.createSession()) {
      return session.dsl().selectFrom(AppForm.class)
        .where(appForm.appId.eq(appId))
        .orderBy(appForm.name, Direction.ASC)
        .execute();
    }
  }

  @Override
  public AppForm save(AppForm form) {
    try (Session session = sessionFactory.createSession()) {
      session.dsl().mergeInto(AppForm.class)
        .values(form)
        .execute();
    }
    return form;
  }

  @Override
  public void deleteByAppId(String appId) {
    try (Session session = sessionFactory.createSession()) {
      session.dsl().deleteFrom(AppForm.class)
        .where(appForm.appId.eq(appId))
        .execute();
    }
  }
}
