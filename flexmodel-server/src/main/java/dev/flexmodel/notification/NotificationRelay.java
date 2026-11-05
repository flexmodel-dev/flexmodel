package dev.flexmodel.notification;

import dev.flexmodel.reflect.ReflectionUtils;
import dev.flexmodel.codegen.entity.NotificationEvent;
import dev.flexmodel.common.persistence.ServerSessions;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Map;

import static dev.flexmodel.query.Expressions.field;
import static dev.flexmodel.common.Values.text;

/**
 * A wake signal is disposable; the ordered event log is the durable source of truth.
 */
@ApplicationScoped
public class NotificationRelay {
  @Inject
  NotificationStream streams;
  @Inject
  ServerSessions sessions;
  private static final org.jboss.logging.Logger LOG = org.jboss.logging.Logger.getLogger(NotificationRelay.class);

  @Scheduled(every = "1s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
  public void dispatch() {
    try (var session = sessions.openOwned("system")) {
      var pending = session.dsl().selectFrom(ReflectionUtils.getModelNameFromClass(NotificationEvent.class)).where(field("published").eq(false)).limit(200).execute();
      for (var event : pending) {
        streams.wake(text(event, "recipient_id"));
        session.mergeUpdate(ReflectionUtils.getModelNameFromClass(NotificationEvent.class), text(event, "id"), Map.of("published", true));
      }
    } catch (Exception error) {
      streams.disconnect();
      LOG.debug("通知实时分发等待恢复", error);
    }
  }
}
