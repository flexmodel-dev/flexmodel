package dev.flexmodel.common.messaging;

import dev.flexmodel.common.AbstractRepository;
import dev.flexmodel.common.ProjectUnitOfWork;
import dev.flexmodel.project.ProjectRepository;
import dev.flexmodel.query.Predicate;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executors;

import static dev.flexmodel.query.Expressions.*;

/**
 * Transactional outbox ordered per aggregate. Existing message rows are the lock anchors.
 */
@ApplicationScoped
public class ReliableMessages extends AbstractRepository {
  private static final org.jboss.logging.Logger LOG = org.jboss.logging.Logger.getLogger(ReliableMessages.class);
  @Inject
  ProjectUnitOfWork unitOfWork;
  @Inject
  ProjectRepository projects;
  @Inject
  Instance<ReliableMessageHandler> handlers;

  public static String orderingKey(String id, Map<String, Object> payload) {
    var variables = payload.get("variables") instanceof Map<?, ?> values ? values : Map.of();
    for (Object value : List.of(Objects.toString(payload.get("orderingKey"), ""), Objects.toString(variables.get("__rootRunId"), ""),
      Objects.toString(variables.get("__runId"), ""), Objects.toString(payload.get("flowInstanceId"), ""), Objects.toString(payload.get("instanceId"), "")))
      if (!value.toString().isBlank()) return value.toString();
    return id;
  }

  public void enqueue(String project, String id, String type, Map<String, Object> payload) {
    unitOfWork.execute(project, () -> {
      try (var session = getProjectSession(project)) {
        if (session.dsl().selectFrom("f_runtime_message").where(field("message_id").eq(id)).count() == 0) {
          session.data().insert("f_runtime_message", new HashMap<>(Map.of("message_id", id, "type", type,
            "ordering_key", orderingKey(id, payload), "payload", payload, "state", "PENDING", "attempts", 0,
            "available_at", LocalDateTime.now(), "created_at", LocalDateTime.now())));
        }
      }
      return null;
    });
  }

  @Scheduled(every = "1s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
  @ActivateRequestContext
  void dispatch() {
    try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
      for (var project : projects.findProjects()) {
        try {
          for (var head : heads(project.getId(), null).stream().limit(40).toList()) {
            String key = Objects.toString(head.get("ordering_key"), head.get("message_id").toString());
            workers.submit(() -> {
              var context = io.quarkus.arc.Arc.container().requestContext();
              context.activate();
              try {
                drain(project.getId(), 40, key);
              } catch (Exception error) {
                LOG.debugf(error, "Outbox delivery deferred for %s/%s", project.getId(), key);
              } finally {
                context.terminate();
              }
            });
          }
        } catch (Exception error) {
          LOG.debugf(error, "Outbox delivery deferred for %s", project.getId());
        }
      }
    }
  }

  private List<Map<String, Object>> heads(String project, String onlyKey) {
    try (var session = getProjectSession(project)) {
      Predicate filter = field("state").eq("PENDING");
      if (onlyKey != null) filter = filter.and(field("ordering_key").eq(onlyKey));
      var pending = session.dsl().selectFrom("f_runtime_message").where(filter).orderBy("id", dev.flexmodel.query.Direction.ASC).execute();
      Map<String, Map<String, Object>> heads = new LinkedHashMap<>();
      pending.forEach(row -> heads.putIfAbsent(Objects.toString(row.get("ordering_key"), row.get("message_id").toString()), row));
      return heads.values().stream().filter(row -> !LocalDateTime.parse(row.get("available_at").toString().replace(' ', 'T')).isAfter(LocalDateTime.now())).toList();
    }
  }

  public int drain(String project, int limit) {
    return drain(project, limit, null);
  }

  @SuppressWarnings("unchecked")
  private int drain(String project, int limit, String onlyKey) {
    int delivered = 0;
    for (int i = 0; i < limit; i++) {
      var ready = heads(project, onlyKey);
      if (ready.isEmpty()) break;
      var message = ready.getFirst();
      Object id = message.get("id");
      Object anchor;
      try (var session = getProjectSession(project)) {
        var first = session.dsl().selectFrom("f_runtime_message").where(field("ordering_key").eq(message.get("ordering_key")))
          .orderBy("id", dev.flexmodel.query.Direction.ASC).limit(1).executeOne();
        anchor = first == null ? id : first.get("id");
      }
      try {
        boolean processed = unitOfWork.execute(project, () -> {
          ProjectUnitOfWork.lock(project, "f_runtime_message", "id", anchor);
          ProjectUnitOfWork.lock(project, "f_runtime_message", "id", id);
          try (var session = getProjectSession(project)) {
            var latest = session.data().findById("f_runtime_message", id).orElseThrow();
            if (!"PENDING".equals(latest.get("state"))) return false;
            var previous = session.dsl().selectFrom("f_runtime_message").where(field("ordering_key").eq(latest.get("ordering_key"))
              .and(field("state").eq("PENDING")).and(field("id").lt(id))).execute();
            if (!previous.isEmpty() || LocalDateTime.parse(latest.get("available_at").toString().replace(' ', 'T')).isAfter(LocalDateTime.now()))
              return false;
            var handler = handlers.stream().filter(item -> item.type().equals(latest.get("type"))).findFirst()
              .orElseThrow(() -> new IllegalStateException("未注册消息处理器: " + latest.get("type")));
            handler.handle(project, String.valueOf(latest.get("message_id")), (Map<String, Object>) latest.get("payload"));
            session.data().updateById("f_runtime_message", Map.of("state", "DONE", "error", ""), id);
            return true;
          }
        });
        if (processed) delivered++;
      } catch (Exception error) {
        unitOfWork.execute(project, () -> {
          ProjectUnitOfWork.lock(project, "f_runtime_message", "id", anchor);
          ProjectUnitOfWork.lock(project, "f_runtime_message", "id", id);
          try (var session = getProjectSession(project)) {
            var latest = session.data().findById("f_runtime_message", id).orElseThrow();
            if (!"PENDING".equals(latest.get("state"))) return null;
            int attempts = ((Number) latest.get("attempts")).intValue() + 1;
            session.data().updateById("f_runtime_message", Map.of("attempts", attempts, "state", attempts >= 8 ? "FAILED" : "PENDING",
              "error", Objects.toString(error.getMessage(), error.getClass().getSimpleName()),
              "available_at", LocalDateTime.now().plusSeconds(Math.min(300, 1L << attempts))), id);
          }
          return null;
        });
        LOG.warnf("Outbox message %s failed: %s", message.get("message_id"), error.getMessage());
      }
    }
    return delivered;
  }
}
