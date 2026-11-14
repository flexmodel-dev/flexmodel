package dev.flexmodel.common.messaging;

import dev.flexmodel.codegen.entity.RuntimeMessage;
import dev.flexmodel.common.AbstractRepository;
import dev.flexmodel.common.ProjectUnitOfWork;
import dev.flexmodel.query.Direction;
import dev.flexmodel.query.Predicate;
import dev.flexmodel.session.Session;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static dev.flexmodel.codegen.System.runtimeMessage;
import static dev.flexmodel.query.Expressions.TRUE;
import static dev.flexmodel.query.Expressions.field;

@ApplicationScoped
public class RuntimeMessageRepository extends AbstractRepository {

  public List<Map<String, Object>> findByMessageId(String appId, String messageId) {
    return findAll(appId, runtimeMessage.messageId.eq(messageId));
  }

  public List<Map<String, Object>> findByType(String appId, String type) {
    return findAll(appId, runtimeMessage.type.eq(type));
  }

  public List<Map<String, Object>> findPending(String appId) {
    return findAll(appId, runtimeMessage.state.eq("PENDING"));
  }

  public List<Map<String, Object>> findUndone(String appId) {
    return findAll(appId, runtimeMessage.state.ne("DONE"));
  }

  public List<Map<String, Object>> findByTypeUndone(String appId, String type) {
    return findAll(appId, runtimeMessage.type.eq(type).and(runtimeMessage.state.ne("DONE")));
  }

  public boolean existsByMessageId(String appId, String messageId) {
    try (Session session = openSession()) {
      return session.dsl().selectFrom(RuntimeMessage.class)
        .where(runtimeMessage.projectId.eq(appId).and(runtimeMessage.messageId.eq(messageId))).count() > 0;
    }
  }

  public void insert(String appId, String messageId, String type, String orderingKey, Map<String, Object> payload) {
    RuntimeMessage values = new RuntimeMessage();
    values.setProjectId(appId);
    values.setMessageId(messageId);
    values.setType(type);
    values.setOrderingKey(orderingKey);
    values.setPayload(payload);
    values.setState("PENDING");
    values.setAttempts(0);
    values.setAvailableAt(LocalDateTime.now());
    values.setCreatedAt(LocalDateTime.now());
    try (Session session = openSession()) {
      session.dsl().insertInto(RuntimeMessage.class).values(values).execute();
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public Map<String, Object> findById(String appId, Object id) {
    try (Session session = openSession()) {
      Map row = session.dsl().selectFrom(RuntimeMessage.class)
        .where(runtimeMessage.projectId.eq(appId).and(field("id").eq(id))).executeOne(Map.class);
      return row;
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public Map<String, Object> findFirstByOrderingKey(String appId, String orderingKey) {
    try (Session session = openSession()) {
      Map row = session.dsl().selectFrom(RuntimeMessage.class)
        .where(runtimeMessage.projectId.eq(appId).and(runtimeMessage.orderingKey.eq(orderingKey)))
        .orderBy("id", Direction.ASC).limit(1).executeOne(Map.class);
      return row;
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public List<Map<String, Object>> findPendingOrdered(String appId, String orderingKey) {
    Predicate filter = runtimeMessage.projectId.eq(appId).and(runtimeMessage.state.in(List.of("PENDING", "FAILED")));
    if (orderingKey != null) filter = filter.and(runtimeMessage.orderingKey.eq(orderingKey));
    try (Session session = openSession()) {
      List<Map> rows = session.dsl().selectFrom(RuntimeMessage.class).where(filter).orderBy("id", Direction.ASC).execute(Map.class);
      return (List<Map<String, Object>>) (List<?>) rows;
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public List<Map<String, Object>> findPendingBefore(String appId, String orderingKey, Object id) {
    try (Session session = openSession()) {
      List<Map> rows = session.dsl().selectFrom(RuntimeMessage.class)
        .where(runtimeMessage.projectId.eq(appId).and(runtimeMessage.orderingKey.eq(orderingKey))
          .and(runtimeMessage.state.in(java.util.List.of("PENDING", "FAILED"))).and(field("id").lt(id))).execute(Map.class);
      return (List<Map<String, Object>>) (List<?>) rows;
    }
  }

  public void lock(String appId, Object id) {
    ProjectUnitOfWork.lock(appId, RuntimeMessage.class, "id", id);
  }

  public void update(String appId, Object id, Map<String, Object> values) {
    try (Session session = openSession()) {
      var update = session.dsl().update(RuntimeMessage.class);
      values.forEach(update::set);
      update.where(field("project_id").eq(appId).and(field("id").eq(id))).execute();
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private List<Map<String, Object>> findAll(String appId, Predicate predicate) {
    try (Session session = openSession()) {
      List<Map> rows = session.dsl().selectFrom(RuntimeMessage.class)
        .where(runtimeMessage.projectId.eq(appId).and(predicate == null ? TRUE : predicate)).execute(Map.class);
      return (List<Map<String, Object>>) (List<?>) rows;
    }
  }
}
