package dev.flexmodel.auth.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import dev.flexmodel.codegen.entity.ResourceTemplate;
import dev.flexmodel.session.Session;
import dev.flexmodel.session.SessionFactory;

import java.util.List;

import static dev.flexmodel.query.Expressions.field;

@ApplicationScoped
public class ResourceTemplateFmRepository implements ResourceTemplateRepository {

  @Inject
  SessionFactory sessionFactory;

  @Override
  public ResourceTemplate findById(Long templateId) {
    try (Session session = sessionFactory.createSession()) {
      return session.dsl()
        .selectFrom(ResourceTemplate.class)
        .where(field(ResourceTemplate::getId).eq(templateId))
        .executeOne();
    }
  }

  @Override
  public List<ResourceTemplate> findAll() {
    try (Session session = sessionFactory.createSession()) {
      return session.dsl()
        .selectFrom(ResourceTemplate.class)
        .execute();
    }
  }

  @Override
  public ResourceTemplate save(ResourceTemplate resourceTemplate) {
    try (Session session = sessionFactory.createSession()) {
      session.dsl()
        .mergeInto(ResourceTemplate.class)
        .values(resourceTemplate)
        .execute();
    }
    return resourceTemplate;
  }

  @Override
  public void delete(Long templateId) {
    try (Session session = sessionFactory.createSession()) {
      session.dsl()
        .deleteFrom(ResourceTemplate.class)
        .where(field(ResourceTemplate::getId).eq(templateId))
        .execute();
    }
  }
}
