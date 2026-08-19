package dev.flexmodel.flow.event;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.RabbitMQContainer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Testcontainers 启动 RabbitMQ broker，并向 Quarkus 注入连接配置与启用 flow-events-out 通道。
 * <p>
 * 用于端到端验证 FlowEventRabbitmqBridge 将事件以正确 routing key 与 JSON 载荷推送到 topic 交换机。
 * <p>
 * 连接配置注入到 SmallRye RabbitMQ connector 的 <b>channel 级</b>属性
 * （{@code mp.messaging.outgoing.flow-events-out.host/port/username/password}）。
 * 注意：{@code quarkus-messaging-rabbitmq} 扩展的 {@code quarkus.rabbitmq.*} 只注册 DevServices 与
 * credentials provider 字段，<b>不注册</b> host/port/username/password 连接字段——这些由 SmallRye
 * connector 自身读取（channel 级或全局 {@code rabbitmq-*} 别名）。显式提供 channel 级连接配置后，
 * DevServices 自动旁路。
 *
 * @author cjbi
 */
public class RabbitMqTestResource implements QuarkusTestResourceLifecycleManager {

  // 静态引用，供测试用例获取连接坐标（测试侧 amqp-client 用其订阅交换机）
  static volatile String host;
  static volatile int port;
  static volatile String username = "guest";
  static volatile String password = "guest";

  private RabbitMQContainer rabbitmq;

  @Override
  public Map<String, String> start() {
    rabbitmq = new RabbitMQContainer("rabbitmq:3-management");
    rabbitmq.start();
    host = rabbitmq.getHost();
    port = rabbitmq.getAmqpPort();

    Map<String, String> config = new LinkedHashMap<>();
    // SmallRye RabbitMQ connector channel 级连接配置（quarkus.rabbitmq.* 不注册连接字段）
    config.put("mp.messaging.outgoing.flow-events-out.host", host);
    config.put("mp.messaging.outgoing.flow-events-out.port", String.valueOf(port));
    config.put("mp.messaging.outgoing.flow-events-out.username", username);
    config.put("mp.messaging.outgoing.flow-events-out.password", password);
    // 显式提供连接配置后 DevServices 自动旁路；此处再确认禁用，避免无 Docker 时误触
    config.put("quarkus.rabbitmq.devservices.enabled", "false");
    // 启用 flow-events-out 通道，使桥接真正转发到 broker
    config.put("mp.messaging.outgoing.flow-events-out.enabled", "true");
    return config;
  }

  @Override
  public void stop() {
    if (rabbitmq != null) {
      rabbitmq.close();
    }
  }
}
