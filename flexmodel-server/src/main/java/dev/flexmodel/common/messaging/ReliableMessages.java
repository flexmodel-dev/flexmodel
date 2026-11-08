package dev.flexmodel.common.messaging;

import dev.flexmodel.common.ProjectUnitOfWork;
import dev.flexmodel.project.ProjectRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executors;

/**
 * Transactional outbox ordered per aggregate. Existing message rows are the lock anchors.
 */
@ApplicationScoped
public class ReliableMessages {
  private static final org.jboss.logging.Logger LOG = org.jboss.logging.Logger.getLogger(ReliableMessages.class);
  @Inject
  ProjectUnitOfWork unitOfWork;
  @Inject
  ProjectRepository projects;
  @Inject
  RuntimeMessageRepository runtimeMessageRepository;
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
    runtimeMessageRepository.inSchema(null, () -> unitOfWork.executePlatform(project, () -> {
      if (!runtimeMessageRepository.existsByMessageId(project, id))
        runtimeMessageRepository.insert(project, id, type, orderingKey(id, payload), payload);
      return null;
    }));
  }

  @Scheduled(every = "1s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
  @ActivateRequestContext
  void dispatch() {
    try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
      for (var project : projects.findProjects()) {
        if (!"system".equals(project.getDatabaseName())) {
          try {
            runtimeMessageRepository.inSchema(project.getDatabaseName(), () -> drain(project.getId(), 40));
          } catch (Exception error) {
            LOG.debugf(error, "Business outbox deferred: %s", project.getId());
          }
        }
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
    Map<String, Map<String, Object>> heads = new LinkedHashMap<>();
    runtimeMessageRepository.findPendingOrdered(project, onlyKey)
      .forEach(row -> heads.putIfAbsent(Objects.toString(row.get("ordering_key"), row.get("message_id").toString()), row));
    return heads.values().stream().filter(row -> "PENDING".equals(row.get("state")) && !availableAt(row).isAfter(LocalDateTime.now())).toList();
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
      var first = runtimeMessageRepository.findFirstByOrderingKey(project, String.valueOf(message.get("ordering_key")));
      Object anchor = first == null ? id : first.get("id");
      try {
        boolean processed = transaction(project, () -> {
          runtimeMessageRepository.lock(project, anchor);
          runtimeMessageRepository.lock(project, id);
          var latest = runtimeMessageRepository.findById(project, id);
          if (latest == null) return false;
          if (!"PENDING".equals(latest.get("state"))) return false;
          var previous = runtimeMessageRepository.findPendingBefore(project, String.valueOf(latest.get("ordering_key")), id);
          if (!previous.isEmpty() || availableAt(latest).isAfter(LocalDateTime.now())) return false;
          var handler = handlers.stream().filter(item -> item.type().equals(latest.get("type"))).findFirst()
            .orElseThrow(() -> new IllegalStateException("未注册消息处理器: " + latest.get("type")));
          if (runtimeMessageRepository.schema() == null)
            handler.handle(project, String.valueOf(latest.get("message_id")), (Map<String, Object>) latest.get("payload"));
          else runtimeMessageRepository.inSchema(null, () -> unitOfWork.executePlatform(project, () -> {
            handler.handle(project, String.valueOf(latest.get("message_id")), (Map<String, Object>) latest.get("payload"));
            return null;
          }));
          runtimeMessageRepository.update(project, id, Map.of("state", "DONE", "error", ""));
          return true;
        });
        if (processed) delivered++;
      } catch (Exception error) {
        transaction(project, () -> {
          runtimeMessageRepository.lock(project, anchor);
          runtimeMessageRepository.lock(project, id);
          var latest = runtimeMessageRepository.findById(project, id);
          if (latest == null) return null;
          if (!"PENDING".equals(latest.get("state"))) return null;
          int attempts = ((Number) latest.get("attempts")).intValue() + 1;
          runtimeMessageRepository.update(project, id, Map.of("attempts", attempts, "state", attempts >= 8 ? "FAILED" : "PENDING",
            "error", Objects.toString(error.getMessage(), error.getClass().getSimpleName()),
            "available_at", LocalDateTime.now().plusSeconds(Math.min(300, 1L << attempts))));
          return null;
        });
        LOG.warnf(error, "Outbox message %s failed: %s", message.get("message_id"), error.getMessage());
      }
    }
    return delivered;
  }

  private <T> T transaction(String app, java.util.function.Supplier<T> action) {
    return runtimeMessageRepository.schema() == null ? unitOfWork.executePlatform(app, action) : unitOfWork.execute(app, action);
  }

  public void enqueueBusiness(String app, String schema, String id, String type, Map<String, Object> payload) {
    runtimeMessageRepository.inSchema(schema, () -> unitOfWork.execute(app, () -> {
      if (!runtimeMessageRepository.existsByMessageId(app, id))
        runtimeMessageRepository.insert(app, id, type, orderingKey(id, payload), payload);
      return null;
    }));
  }

  public int drainBusiness(String app, String schema, int limit) {
    return runtimeMessageRepository.inSchema(schema, () -> drain(app, limit));
  }

  public Object businessEffect(String app, String schema, String task, java.util.function.Supplier<Object> action) {
    return runtimeMessageRepository.inSchema(schema, () -> unitOfWork.execute(app, () -> {
      String key = "effect:" + task;
      var old = runtimeMessageRepository.findByMessageId(app, key);
      if (!old.isEmpty()) return dev.flexmodel.common.Values.object(old.getFirst().get("payload")).get("result");
      Object result = action.get();
      var payload = new LinkedHashMap<String, Object>();
      payload.put("result", result);
      runtimeMessageRepository.insert(app, key, "flow.effect.receipt", key, payload);
      var receipt = runtimeMessageRepository.findByMessageId(app, key).getFirst();
      runtimeMessageRepository.update(app, receipt.get("id"), Map.of("state", "RECEIPT"));
      return result;
    }));
  }

  private static LocalDateTime availableAt(Map<String, Object> row) {
    return LocalDateTime.parse(row.get("available_at").toString().replace(' ', 'T'));
  }
}
