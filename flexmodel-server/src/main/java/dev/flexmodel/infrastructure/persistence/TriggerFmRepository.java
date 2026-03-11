package dev.flexmodel.infrastructure.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import dev.flexmodel.codegen.entity.Trigger;
import dev.flexmodel.domain.model.schedule.TriggerRepository;
import dev.flexmodel.query.Predicate;
import dev.flexmodel.session.Session;

import java.util.List;

import static dev.flexmodel.query.Expressions.field;

@ApplicationScoped
public class TriggerFmRepository extends AbstractRepository implements TriggerRepository {

  @Override
  public Trigger findById(String projectId, String id) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl()
        .select()
        .from(Trigger.class)
        .where(field(Trigger::getProjectId).eq(projectId).and(field(Trigger::getId).eq(id)))
        .executeOne();
    }
  }

  @Override
  public Trigger save(String projectId, Trigger trigger) {
    try (Session session = getProjectSession(projectId)) {
      session.dsl()
        .mergeInto(Trigger.class)
        .values(trigger)
        .execute();
    }
    return trigger;
  }

  @Override
  public void deleteById(String projectId, String id) {
    try (Session session = getProjectSession(projectId)) {
      session.dsl()
        .deleteFrom(Trigger.class)
        .where(field(Trigger::getProjectId).eq(projectId).and(field(Trigger::getId).eq(id)))
        .execute();
    }
  }

  @Override
  public List<Trigger> find(String projectId, Predicate filter, Integer page, Integer size) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl()
        .select()
        .from(Trigger.class)
        .where(field(Trigger::getProjectId).eq(projectId).and(filter))
        .page(page, size)
        .orderByDesc(Trigger::getCreatedAt)
        .execute();
    }
  }

  @Override
  public long count(String projectId, Predicate filter) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl()
        .select()
        .from(Trigger.class)
        .where(field(Trigger::getProjectId).eq(projectId).and(filter))
        .count();
    }
  }

}
