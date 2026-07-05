package dev.flexmodel.scheduling;

import dev.flexmodel.codegen.entity.Trigger;
import dev.flexmodel.common.AbstractRepository;
import dev.flexmodel.query.Predicate;
import dev.flexmodel.session.Session;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

import static dev.flexmodel.codegen.System.trigger;

@ApplicationScoped
public class TriggerFmRepository extends AbstractRepository implements TriggerRepository {

  @Override
  public Trigger findById(String projectId, String id) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl()
        .select()
        .from(Trigger.class)
        .where(trigger.id.eq(id))
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
        .where(trigger.id.eq(id))
        .execute();
    }
  }

  @Override
  public List<Trigger> find(String projectId, Predicate filter, Integer page, Integer size) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl()
        .select()
        .from(Trigger.class)
        .where(filter)
        .page(page, size)
        .orderByDesc("createdAt")
        .execute();
    }
  }

  @Override
  public long count(String projectId, Predicate filter) {
    try (Session session = getProjectSession(projectId)) {
      return session.dsl()
        .select()
        .from(Trigger.class)
        .where(filter)
        .count();
    }
  }

}
