package dev.flexmodel.notification;

import dev.flexmodel.common.persistence.*;
import dev.flexmodel.query.*;
import dev.flexmodel.session.Session;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;

import static dev.flexmodel.query.Expressions.*;

/**
 * All inbox mutations serialize on the recipient's existing system user row.
 */
@ApplicationScoped
public class NotificationRepository {
  @Inject
  ServerSessions sessions;
  @Inject
  NotificationRelay relay;

  public boolean activeUser(String user) {
    try (var session = sessions.openOwned("system")) {
      return session.data().findById("f_user", user).filter(row -> "ACTIVE".equals(text(row, "status"))).isPresent();
    }
  }

  public static String text(Map<String, Object> row, String key) {
    return Objects.toString(row.get(key), "");
  }

  public static long number(Map<String, Object> row, String key) {
    return ((Number) row.getOrDefault(key, 0L)).longValue();
  }

  public static void update(Session session, String model, String id, Map<String, Object> values) {
    var row = new HashMap<>(session.data().findById(model, id).orElseThrow());
    row.putAll(values);
    session.data().updateById(model, row, id);
  }

  private <T> T mutate(String user, Function<ManagedSession, T> action) {
    T result;
    try (var session = sessions.openOwned("system")) {
      session.startTransaction();
      try {
        if (session.lockRow("f_user", "id", user) == 0) throw new NotFoundException();
        if (session.data().findById("f_notification_stream", user).isEmpty())
          session.data().insert("f_notification_stream", new HashMap<>(Map.of("id", user, "version", 0L, "unread", 0L)));
        result = action.apply(session);
        session.commit();
      } catch (RuntimeException error) {
        session.rollback();
        throw error;
      }
    }
    relay.wake(user);
    return result;
  }

  private void event(Session session, String user, String type, long unreadDelta, Map<String, Object> payload) {
    var state = session.data().findById("f_notification_stream", user).orElseThrow();
    long version = number(state, "version") + 1, unread = number(state, "unread") + unreadDelta;
    update(session, "f_notification_stream", user, Map.of("version", version, "unread", unread));
    var data = new HashMap<>(payload);
    data.put("version", Long.toString(version));
    data.put("unread", unread);
    session.data().insert("f_notification_event", new HashMap<>(Map.of("id", user + ":" + version, "recipient_id", user,
      "version", version, "type", type, "payload", data, "published", false, "created_at", LocalDateTime.now())));
  }

  public void deliver(String app, String key, String user, Map<String, Object> payload) {
    mutate(user, session -> {
      var filter = field("app_id").eq(app).and(field("event_key").eq(key)).and(field("recipient_id").eq(user));
      if (session.dsl().selectFrom("f_notification").where(filter).count() > 0) return null;
      var row = new HashMap<>(payload);
      row.remove("recipients");
      row.remove("orderingKey");
      row.putAll(Map.of("id", UUID.randomUUID().toString(), "app_id", app, "event_key", key, "recipient_id", user, "received_at", LocalDateTime.now()));
      row.put("is_read", false);
      session.data().insert("f_notification", row);
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
    try (var session = sessions.openOwned("system")) {
      var filter = filter(user, status, category, app);
      int limit = Math.clamp(size, 1, 100);
      var list = session.dsl().selectFrom("f_notification").where(filter).orderBy(new Query.OrderBy().desc("received_at").desc("id"))
        .page(Math.clamp(page, 1, Integer.MAX_VALUE / limit), limit).execute();
      return Map.of("list", list.stream().map(row -> dto(row, false)).toList(), "total", session.dsl().selectFrom("f_notification").where(filter).count());
    }
  }

  public Map<String, Object> detail(String user, String id) {
    try (var session = sessions.openOwned("system")) {
      var row = session.data().findById("f_notification", id).filter(item -> user.equals(text(item, "recipient_id"))).orElseThrow(NotFoundException::new);
      return dto(row, true);
    }
  }

  public Map<String, Object> summary(String user) {
    return mutate(user, session -> {
      var state = session.data().findById("f_notification_stream", user).orElseThrow();
      Map<String, Map<String, Object>> apps = new LinkedHashMap<>();
      Map<String, Long> categories = new LinkedHashMap<>(Map.of("approval", 0L, "workflow", 0L));
      for (var row : session.dsl().select(s -> s.field("app_id").max("name", "app_name")).from("f_notification")
        .where(field("recipient_id").eq(user)).groupBy("app_id").execute())
        apps.put(text(row, "app_id"), new HashMap<>(Map.of("appId", text(row, "app_id"), "name", text(row, "name"), "unread", 0L)));
      for (var row : session.dsl().select(s -> s.field("app_id").field("category").count("n", "id")).from("f_notification")
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
      if (id != null && session.data().findById("f_notification", id).filter(row -> user.equals(text(row, "recipient_id"))).isEmpty())
        throw new NotFoundException();
      var condition = filter(user, "unread", category, app);
      if (id != null) condition = condition.and(field("id").eq(id));
      int updated = session.data().update("f_notification", new HashMap<>(Map.of("read_at", LocalDateTime.now(), "is_read", true)), condition);
      if (updated > 0)
        event(session, user, id == null ? "notification.read_all" : "notification.read", -updated, id == null ? Map.of() : Map.of("notificationId", id));
      return (long) updated;
    });
  }

  public List<Map<String, Object>> events(String user, long after, int limit) {
    try (var session = sessions.openOwned("system")) {
      return session.dsl().selectFrom("f_notification_event").where(field("recipient_id").eq(user).and(field("version").gt(after)))
        .orderBy("version", Direction.ASC).limit(limit).execute();
    }
  }

  public Map<String, Object> state(String user) {
    try (var session = sessions.openOwned("system")) {
      return session.data().findById("f_notification_stream", user).orElse(Map.of("version", 0L, "unread", 0L));
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
