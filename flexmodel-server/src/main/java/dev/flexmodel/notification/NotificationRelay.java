package dev.flexmodel.notification;

import dev.flexmodel.common.FlexmodelConfig;
import dev.flexmodel.common.persistence.ServerSessions;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.*;

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
  @Inject
  FlexmodelConfig config;
  @ConfigProperty(name = "mp.messaging.connector.smallrye-rabbitmq.host", defaultValue = "localhost")
  String host;
  @ConfigProperty(name = "mp.messaging.connector.smallrye-rabbitmq.port", defaultValue = "5672")
  int port;
  @ConfigProperty(name = "mp.messaging.connector.smallrye-rabbitmq.username", defaultValue = "guest")
  String username;
  @ConfigProperty(name = "mp.messaging.connector.smallrye-rabbitmq.password", defaultValue = "guest")
  String password;
  @ConfigProperty(name = "mp.messaging.connector.smallrye-rabbitmq.virtual-host", defaultValue = "/")
  String virtualHost;
  private com.rabbitmq.client.Connection connection;
  private com.rabbitmq.client.Channel channel;
  private static final String EXCHANGE = "flexmodel.notifications";
  private static final org.jboss.logging.Logger LOG = org.jboss.logging.Logger.getLogger(NotificationRelay.class);

  private synchronized void connect() throws Exception {
    if (channel != null && channel.isOpen()) return;
    var factory = new com.rabbitmq.client.ConnectionFactory();
    factory.setHost(host);
    factory.setPort(port);
    factory.setUsername(username);
    factory.setPassword(password);
    factory.setVirtualHost(virtualHost);
    factory.setConnectionTimeout(3000);
    factory.setAutomaticRecoveryEnabled(false);
    connection = factory.newConnection();
    channel = connection.createChannel();
    channel.exchangeDeclare(EXCHANGE, "fanout", true);
    String queue = channel.queueDeclare("", false, true, true, Map.of()).getQueue();
    channel.queueBind(queue, EXCHANGE, "");
    channel.confirmSelect();
    channel.basicConsume(queue, true, (_, delivery) -> streams.wake(new String(delivery.getBody(), java.nio.charset.StandardCharsets.UTF_8)), _ -> streams.disconnect());
    connection.addShutdownListener(_ -> streams.disconnect());
    // Queue recreation loses transient signals. Reconnect all streams to replay from the database.
    streams.disconnect();
  }

  @Scheduled(every = "1s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
  public void dispatch() {
    try {
      boolean broker = config.events().rabbitmq().enabled();
      if (broker) connect();
      try (var session = sessions.openOwned("system")) {
        var pending = session.dsl().selectFrom("f_notification_event").where(field("published").eq(false)).limit(200).execute();
        for (var event : pending) {
          String user = text(event, "recipient_id");
          if (broker) synchronized (this) {
            channel.basicPublish(EXCHANGE, "", null, user.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            channel.waitForConfirmsOrDie(3000);
          }
          streams.wake(user);
          session.mergeUpdate("f_notification_event", text(event, "id"), Map.of("published", true));
        }
      }
    } catch (Exception error) {
      streams.disconnect();
      close();
      LOG.debug("通知实时分发等待恢复", error);
    }
  }

  @PreDestroy
  synchronized void close() {
    if (connection != null) try {
      connection.close();
    } catch (Exception ignored) {
    }
    channel = null;
    connection = null;
  }
}
