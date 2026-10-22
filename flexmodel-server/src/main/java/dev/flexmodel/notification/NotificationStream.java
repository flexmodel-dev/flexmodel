package dev.flexmodel.notification;

import dev.flexmodel.JsonUtils;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.sse.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static dev.flexmodel.notification.NotificationRepository.*;

/**
 * Only explicit user IDs captured at authenticated connection time cross into background work.
 */
@ApplicationScoped
public class NotificationStream {
  private static final org.jboss.logging.Logger LOG = org.jboss.logging.Logger.getLogger(NotificationStream.class);
  @Inject
  NotificationRepository repository;
  private final Set<Client> clients = ConcurrentHashMap.newKeySet();
  private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
  private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();

  private class Client {
    String user;
    long cursor;
    Instant expiry;
    SseEventSink sink;
    Sse sse;
    boolean ready;
    ScheduledFuture<?> expiryTask;

    void send(String type, String id, Object data) throws Exception {
      var event = sse.newEventBuilder().name(type).data(String.class, JsonUtils.toJsonString(data));
      if (id != null) event.id(id);
      sink.send(event.build()).toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    synchronized void pump(boolean heartbeat) {
      if (!clients.contains(this)) return;
      try {
        if (sink.isClosed() || Instant.now().isAfter(expiry) || heartbeat && !repository.activeUser(user)) {
          drop();
          return;
        }
        if (!ready) {
          var state = repository.state(user);
          long current = number(state, "version");
          var replay = cursor < 0 || cursor > current ? List.<Map<String, Object>>of() : repository.events(user, cursor, 1001);
          if (cursor < 0 || cursor > current || replay.size() > 1000) {
            cursor = current;
            send("snapshot", Long.toString(cursor), Map.of("version", Long.toString(cursor), "unread", number(state, "unread")));
          } else for (var row : replay) emit(row, true);
          ready = true;
          send("ready", Long.toString(cursor), Map.of("version", Long.toString(cursor)));
        }
        for (int batch = 0; batch < 10; batch++) {
          var events = repository.events(user, cursor, 100);
          for (var row : events) emit(row, false);
          if (events.size() < 100) break;
          if (batch == 9) {
            drop();
            return;
          }
        }
        if (heartbeat)
          sink.send(sse.newEventBuilder().comment("heartbeat").build()).toCompletableFuture().get(5, TimeUnit.SECONDS);
      } catch (Exception error) {
        LOG.debug("通知连接关闭", error);
        drop();
      }
    }

    @SuppressWarnings("unchecked")
    void emit(Map<String, Object> row, boolean replay) throws Exception {
      var payload = new HashMap<>((Map<String, Object>) row.get("payload"));
      payload.put("replay", replay);
      long next = number(row, "version");
      send(text(row, "type"), Long.toString(next), payload);
      cursor = next;
    }

    void drop() {
      clients.remove(this);
      if (expiryTask != null) expiryTask.cancel(false);
      if (!sink.isClosed()) sink.close();
    }
  }

  public void open(String user, String last, Instant expiry, SseEventSink sink, Sse sse) {
    Client client = new Client();
    client.user = user;
    client.expiry = expiry;
    client.sink = sink;
    client.sse = sse;
    try {
      client.cursor = last == null || last.isBlank() ? -1 : Long.parseLong(last);
    } catch (NumberFormatException error) {
      client.cursor = -1;
    }
    clients.add(client);
    client.expiryTask = timer.schedule(client::drop, Math.max(0, java.time.Duration.between(Instant.now(), expiry).toMillis()), TimeUnit.MILLISECONDS);
    workers.submit(() -> client.pump(false));
  }

  @PostConstruct
  void start() {
    timer.scheduleAtFixedRate(() -> clients.forEach(client -> workers.submit(() -> client.pump(true))), 15, 15, TimeUnit.SECONDS);
  }

  public void wake(String user) {
    clients.stream().filter(client -> client.user.equals(user)).forEach(client -> workers.submit(() -> client.pump(false)));
  }

  public void disconnect() {
    clients.forEach(Client::drop);
  }

  @PreDestroy
  void close() {
    disconnect();
    timer.shutdownNow();
    workers.shutdownNow();
  }
}
