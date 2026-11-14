package dev.flexmodel.notification;

import dev.flexmodel.SQLiteTestResource;
import dev.flexmodel.common.persistence.ServerSessions;
import dev.flexmodel.session.SessionFactory;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@TestProfile(NotificationResourceTest.Profile.class)
@QuarkusTestResource(SQLiteTestResource.class)
class NotificationRelayTest {
  @Inject
  ServerSessions sessions;
  @Inject
  SessionFactory factory;
  @Inject
  NotificationRepository repository;

  static class Probe extends NotificationStream {
    final Map<String, Integer> wakes = new ConcurrentHashMap<>();

    @Override
    public void wake(String user) {
      wakes.merge(user, 1, Integer::sum);
    }

    @Override
    public void disconnect() {
    }
  }

  NotificationRelay relay(Probe probe) {
    var relay = new NotificationRelay();
    relay.sessions = sessions;
    relay.streams = probe;
    return relay;
  }

  String user() {
    String user = "relay_" + UUID.randomUUID();
    try (var session = factory.createSession()) {
      session.data().insert("f_user", new HashMap<>(Map.of("id", user, "name", user, "status", "ACTIVE", "password_hash", "unused")));
    }
    return user;
  }

  @Test
  void wakesLocalStreamAndPublishesEachEventOnce() {
    String user = user();
    repository.deliver("dev_test", "relay", user, Map.of("title", "本地分发", "content", "正文", "category", "workflow", "app_name", "应用", "occurred_at", LocalDateTime.now().toString()));
    var probe = new Probe();
    var relay = relay(probe);

    relay.dispatch();
    assertEquals(1, probe.wakes.get(user));

    relay.dispatch();
    assertEquals(1, probe.wakes.get(user));
  }
}
