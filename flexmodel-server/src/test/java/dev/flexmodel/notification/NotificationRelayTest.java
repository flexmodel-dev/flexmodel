package dev.flexmodel.notification;

import dev.flexmodel.SQLiteTestResource;
import dev.flexmodel.common.FlexmodelConfig;
import dev.flexmodel.common.persistence.ServerSessions;
import dev.flexmodel.session.SessionFactory;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.*;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.RabbitMQContainer;

import java.util.*;
import java.util.concurrent.*;
import java.time.*;

import static org.junit.jupiter.api.Assertions.*;

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
    final Set<String> received = ConcurrentHashMap.newKeySet();

    @Override
    public void wake(String user) {
      received.add(user);
    }

    @Override
    public void disconnect() {
    }
  }

  NotificationRelay relay(RabbitMQContainer broker, Probe probe) {
    var relay = new NotificationRelay();
    relay.sessions = sessions;
    relay.streams = probe;
    relay.config = (FlexmodelConfig) java.lang.reflect.Proxy.newProxyInstance(FlexmodelConfig.class.getClassLoader(), new Class<?>[]{FlexmodelConfig.class}, (_, method, _) -> {
      if (method.getName().equals("events")) return (FlexmodelConfig.EventsConfig) () -> () -> true;
      throw new UnsupportedOperationException(method.getName());
    });
    relay.host = broker.getHost();
    relay.port = broker.getAmqpPort();
    relay.username = "guest";
    relay.password = "guest";
    relay.virtualHost = "/";
    return relay;
  }

  String user() {
    String user = "relay_" + UUID.randomUUID();
    try (var session = factory.createSession()) {
      session.data().insert("f_user", new HashMap<>(Map.of("id", user, "name", user, "status", "ACTIVE", "password_hash", "unused")));
    }
    return user;
  }

  void await(Probe probe, String user) throws Exception {
    long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
    while (System.nanoTime() < deadline && !probe.received.contains(user)) Thread.sleep(50);
    assertTrue(probe.received.contains(user), "另一个实例应收到独立队列的广播");
  }

  @Test
  void fansOutToEveryInstanceAndRecoversAfterBrokerRestart() throws Exception {
    try (var broker = new RabbitMQContainer("rabbitmq:3-management")) {
      broker.start();
      var first = new Probe();
      var second = new Probe();
      var a = relay(broker, first);
      var b = relay(broker, second);
      try {
        a.dispatch();
        b.dispatch();
        String user = user();
        repository.deliver("dev_test", "fanout", user, Map.of("title", "多实例", "content", "正文", "category", "workflow", "app_name", "应用", "occurred_at", LocalDateTime.now().toString()));
        a.dispatch();
        await(first, user);
        await(second, user);
        assertEquals(1L, NotificationRepository.number(repository.state(user), "version"));
        broker.execInContainer("rabbitmqctl", "stop_app");
        String offline = user();
        repository.deliver("dev_test", "offline", offline, Map.of("title", "恢复", "content", "正文", "category", "workflow", "app_name", "应用", "occurred_at", LocalDateTime.now().toString()));
        a.dispatch(); // Remains unpublished while the broker is unavailable.
        broker.execInContainer("rabbitmqctl", "start_app");
        a.dispatch();
        await(first, offline);
        b.dispatch();
        assertEquals(1, repository.events(offline, 0, 100).size());
        String restored = user();
        repository.deliver("dev_test", "restored", restored, Map.of("title", "已恢复", "content", "正文", "category", "workflow", "app_name", "应用", "occurred_at", LocalDateTime.now().toString()));
        a.dispatch();
        await(first, restored);
        await(second, restored);
      } finally {
        a.close();
        b.close();
        first.close();
        second.close();
      }
    }
  }
}
