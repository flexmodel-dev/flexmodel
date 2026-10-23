package dev.flexmodel.notification;

import dev.flexmodel.reflect.ReflectionUtils;
import dev.flexmodel.codegen.entity.Notification;
import dev.flexmodel.codegen.entity.NotificationEvent;
import dev.flexmodel.codegen.entity.User;
import dev.flexmodel.common.ProjectUnitOfWork;
import dev.flexmodel.common.persistence.*;
import dev.flexmodel.query.*;
import dev.flexmodel.session.Session;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;

import static dev.flexmodel.common.Values.text;
import static dev.flexmodel.query.Expressions.*;

/**
 * All inbox mutations serialize on the recipient's existing system user row.
 */
@ApplicationScoped
public class NotificationRepository {
  @Inject
  ServerSessions sessions;
  @Inject
  NotificationStream streams;

  public boolean activeUser(String user) {
    try (var session = open()) {
      return session.data().findById(ReflectionUtils.getModelNameFromClass(User.class), user).filter(row -> "ACTIVE".equals(text(row, "status"))).isPresent();
    }
  }

  public static long number(Map<String, Object> row, String key) {
    return ((Number) row.getOrDefault(key, 0L)).longValue();
  }

  /**
   * 复用当前平台事务（若存在），否则开启独立的系统库会话。
   */
  private Session open() {
    Session borrowed = ProjectUnitOfWork.borrowPlatform();
    return borrowed != null ? borrowed : sessions.openOwned("system");
  }

  private <T> T mutate(String user, Function<ManagedSession, T> action) {
    Session borrowed = ProjectUnitOfWork.borrowPlatform();
    if (borrowed instanceof ManagedSession managed) {
      T result = apply(managed, user, action);
      ProjectUnitOfWork.afterCommit(() -> streams.wake(user));
      return result;
    }
    T result;
    try (var session = sessions.openOwned("system")) {
      session.startTransaction();
      try {
        result = apply(session, user, action);
        session.commit();
      } catch (RuntimeException error) {
        session.rollback();
        throw error;
      }
    }
    streams.wake(user);
    return result;
  }

  private <T> T apply(ManagedSession session, String user, Function<ManagedSession, T> action) {
    if (session.lockRow(ReflectionUtils.getModelNameFromClass(User.class), "id", user) == 0)
      throw new NotFoundException();
    if (session.data().findById(ReflectionUtils.getModelNameFromClass(dev.flexmodel.codegen.entity.NotificationStream.class), user).isEmpty())
      session.data().insert(ReflectionUtils.getModelNameFromClass(dev.flexmodel.codegen.entity.NotificationStream.class), new HashMap<>(Map.of("id", user, "version", 0L, "unread", 0L)));
    return action.apply(session);
  }

  private void event(ManagedSession session, String user, String type, long unreadDelta, Map<String, Object> payload) {
    var state = session.data().findById(ReflectionUtils.getModelNameFromClass(dev.flexmodel.codegen.entity.NotificationStream.class), user).orElseThrow();
    long version = number(state, "version") + 1, unread = number(state, "unread") + unreadDelta;
    session.mergeUpdate(ReflectionUtils.getModelNameFromClass(dev.flexmodel.codegen.entity.NotificationStream.class), user, Map.of("version", version, "unread", unread));
    var data = new HashMap<>(payload);
    data.put("version", Long.toString(version));
    data.put("unread", unread);
    session.data().insert(ReflectionUtils.getModelNameFromClass(NotificationEvent.class), new HashMap<>(Map.of("id", user + ":" + version, "recipient_id", user,
      "version", version, "type", type, "payload", data, "published", false, "created_at", LocalDateTime.now())));
  }

  public void deliver(String app, String key, String user, Map<String, Object> payload) {
    mutate(user, session -> {
      var filter = field("app_id").eq(app).and(field("event_key").eq(key)).and(field("recipient_id").eq(user));
      if (session.dsl().selectFrom(ReflectionUtils.getModelNameFromClass(Notification.class)).where(filter).count() > 0)
        return null;
      var row = new HashMap<>(payload);
      row.remove("recipients");
      row.remove("orderingKey");
      row.putAll(Map.of("id", UUID.randomUUID().toString(), "app_id", app, "event_key", key, "recipient_id", user, "received_at", LocalDateTime.now()));
      row.put("is_read", false);
      session.data().insert(ReflectionUtils.getModelNameFromClass(Notification.class), row);
      event(session, user, "notification.created", 1, Map.of("notification", dto(row, false)));
      return null;
    });
  }

  private Predicate filter(String user, String status, String category, String app) {
    Predicate result = field("recipient_id").eq(user);
    if ("unread".equals(status)) result = result.and(field("is_read").eq(false));
    if (category != null && !category.isBlank()) result = result.and(field("category").eq(category));
    if (app != null && !app.isBlank()) result = result.and(field("app_id").eq(app));
    return result;
  }

  public Map<String, Object> list(String user, String status, String category, String app, int page, int size) {
    try (var session = open()) {
      var filter = filter(user, status, category, app);
      int limit = Math.clamp(size, 1, 100);
      var list = session.dsl().selectFrom(ReflectionUtils.getModelNameFromClass(Notification.class)).where(filter).orderBy(new Query.OrderBy().desc("received_at").desc("id"))
        .page(Math.clamp(page, 1, Integer.MAX_VALUE / limit), limit).execute();
      return Map.of("list", list.stream().map(row -> dto(row, false)).toList(), "total", session.dsl().selectFrom(ReflectionUtils.getModelNameFromClass(Notification.class)).where(filter).count());
    }
  }

  public Map<String, Object> detail(String user, String id) {
    try (var session = open()) {
      var row = session.data().findById(ReflectionUtils.getModelNameFromClass(Notification.class), id).filter(item -> user.equals(text(item, "recipient_id"))).orElseThrow(NotFoundException::new);
      return dto(row, true);
    }
  }

  public Map<String, Object> summary(String user) {
    return mutate(user, session -> {
      var state = session.data().findById(ReflectionUtils.getModelNameFromClass(dev.flexmodel.codegen.entity.NotificationStream.class), user).orElseThrow();
      Map<String, Map<String, Object>> apps = new LinkedHashMap<>();
      Map<String, Long> categories = new LinkedHashMap<>(Map.of("approval", 0L, "workflow", 0L));
      for (var row : session.dsl().select(s -> s.field("app_id").max("name", "app_name")).from(ReflectionUtils.getModelNameFromClass(Notification.class))
        .where(field("recipient_id").eq(user)).groupBy("app_id").execute())
        apps.put(text(row, "app_id"), new HashMap<>(Map.of("appId", text(row, "app_id"), "name", text(row, "name"), "unread", 0L)));
      for (var row : session.dsl().select(s -> s.field("app_id").field("category").count("n", "id")).from(ReflectionUtils.getModelNameFromClass(Notification.class))
        .where(filter(user, "unread", null, null)).groupBy("app_id", "category").execute()) {
        var app = apps.get(text(row, "app_id"));
        app.put("unread", number(app, "unread") + number(row, "n"));
        categories.merge(text(row, "category"), number(row, "n"), Long::sum);
      }
      return Map.of("version", Long.toString(number(state, "version")), "unread", number(state, "unread"), "byCategory", categories, "apps", apps.values());
    });
  }

  public long read(String user, String id, String category, String app) {
    return mutate(user, session -> {
      if (id != null && session.data().findById(ReflectionUtils.getModelNameFromClass(Notification.class), id).filter(row -> user.equals(text(row, "recipient_id"))).isEmpty())
        throw new NotFoundException();
      var condition = filter(user, "unread", category, app);
      if (id != null) condition = condition.and(field("id").eq(id));
      int updated = session.data().update(ReflectionUtils.getModelNameFromClass(Notification.class), new HashMap<>(Map.of("read_at", LocalDateTime.now(), "is_read", true)), condition);
      if (updated > 0)
        event(session, user, id == null ? "notification.read_all" : "notification.read", -updated, id == null ? Map.of() : Map.of("notificationId", id));
      return (long) updated;
    });
  }

  public List<Map<String, Object>> events(String user, long after, int limit) {
    try (var session = open()) {
      return session.dsl().selectFrom(ReflectionUtils.getModelNameFromClass(NotificationEvent.class)).where(field("recipient_id").eq(user).and(field("version").gt(after)))
        .orderBy("version", Direction.ASC).limit(limit).execute();
    }
  }

  public Map<String, Object> state(String user) {
    try (var session = open()) {
      return session.data().findById(ReflectionUtils.getModelNameFromClass(dev.flexmodel.codegen.entity.NotificationStream.class), user).orElse(Map.of("version", 0L, "unread", 0L));
    }
  }

  public static Map<String, Object> dto(Map<String, Object> row, boolean full) {
    Map<String, Object> dto = new LinkedHashMap<>();
    for (String key : List.of("id", "category", "title", "content"))
      dto.put(key, full || !key.equals("content") ? row.get(key) : text(row, key).substring(0, Math.min(140, text(row, key).length())));
    for (var key : Map.of("appId", "app_id", "appName", "app_name", "instanceId", "instance_id", "taskId", "task_id", "receivedAt", "received_at", "occurredAt", "occurred_at", "readAt", "read_at").entrySet())
      dto.put(key.getKey(), row.get(key.getValue()));
    return dto;
  }
}
