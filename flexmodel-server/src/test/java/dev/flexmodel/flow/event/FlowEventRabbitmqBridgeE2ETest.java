package dev.flexmodel.flow.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.GetResponse;
import dev.flexmodel.SQLiteTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 端到端验证 {@link FlowEventRabbitmqBridge} 将 Flow 生命周期事件以正确 routing key 与
 * JSON 载荷推送到 RabbitMQ topic 交换机。
 * <p>
 * 使用 Testcontainers 启动真实 RabbitMQ broker（见 {@link RabbitMqTestResource}），
 * 并以 amqp-client 临时队列订阅交换机、拉取转发的消息。
 * <p>
 * 默认跳过（避免无 Docker 环境下默认 {@code mvn test} 失败）。运行需：
 * <ul>
 *   <li>本机 Docker 可用且能拉取 {@code rabbitmq:3-management} 镜像</li>
 *   <li>设置环境变量 {@code FLEXMODEL_E2E_RABBITMQ=true}（opt-in 启用本端到端测试）</li>
 * </ul>
 * <p>
 * {@code @QuarkusTestResource} 设 {@code restrictToAnnotatedClass=true}，确保 broker 资源
 * 仅对本测试类生效，不泄漏到其他共享应用上下文的测试。
 *
 * @author cjbi
 */
@EnabledIfEnvironmentVariable(named = "FLEXMODEL_E2E_RABBITMQ", matches = "true")
@QuarkusTest
@QuarkusTestResource(SQLiteTestResource.class)
@QuarkusTestResource(value = RabbitMqTestResource.class, restrictToAnnotatedClass = true)
public class FlowEventRabbitmqBridgeE2ETest {

  private static final String EXCHANGE = "flexmodel.flow.events";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Inject
  FlowEventPublisher flowEventPublisher;

  /**
   * 验证 FlowDeployedEvent 经桥接转发后，AMQP routing key 与 JSON 载荷字段正确。
   */
  @Test
  void flowDeployedEventForwardedToBroker() throws Exception {
    String projectId = "proj-e2e";
    String caller = "caller-e2e";
    String flowModuleId = "module-e2e";
    String flowDeployId = "deploy-e2e";

    try (Connection connection = newConnection();
         Channel channel = connection.createChannel()) {
      // 确保交换机存在（与 SmallRye 声明参数一致：topic、durable），幂等
      channel.exchangeDeclare(EXCHANGE, BuiltinExchangeType.TOPIC, true);
      String queue = channel.queueDeclare().getQueue();
      channel.queueBind(queue, EXCHANGE, FlowEventTypes.FLOW_DEPLOYED);

      flowEventPublisher.publish(
        new FlowDeployedEvent(projectId, caller, flowModuleId, flowDeployId));

      GetResponse response = pollForMessage(channel, queue, 15000L);
      assertNotNull(response, "FlowDeployedEvent should be forwarded to broker within timeout");

      Envelope envelope = response.getEnvelope();
      assertEquals(FlowEventTypes.FLOW_DEPLOYED, envelope.getRoutingKey(),
        "AMQP routing key should match event routing key");

      JsonNode body = MAPPER.readTree(response.getBody());
      assertEquals(projectId, body.get("projectId").asText());
      assertEquals(caller, body.get("caller").asText());
      assertEquals(flowModuleId, body.get("flowModuleId").asText());
      assertEquals(flowDeployId, body.get("flowDeployId").asText());
      assertTrue(body.get("timestamp").asLong() > 0, "timestamp should be set");
    }
  }

  /**
   * 验证带 variables 快照的 FlowInstanceStartedEvent 经桥接转发后，variables 仍在 JSON 载荷中完整。
   */
  @Test
  void flowInstanceStartedEventWithVariablesForwarded() throws Exception {
    String projectId = "proj-vars";
    String caller = "caller-vars";
    String flowDeployId = "deploy-vars";
    String flowInstanceId = "instance-vars";
    Map<String, Object> variables = new HashMap<>();
    variables.put("amount", 100);
    variables.put("approved", true);

    try (Connection connection = newConnection();
         Channel channel = connection.createChannel()) {
      channel.exchangeDeclare(EXCHANGE, BuiltinExchangeType.TOPIC, true);
      String queue = channel.queueDeclare().getQueue();
      channel.queueBind(queue, EXCHANGE, FlowEventTypes.FLOW_INSTANCE_STARTED);

      flowEventPublisher.publish(
        new FlowInstanceStartedEvent(projectId, caller, flowDeployId, flowInstanceId, variables));

      GetResponse response = pollForMessage(channel, queue, 15000L);
      assertNotNull(response, "FlowInstanceStartedEvent should be forwarded to broker within timeout");

      assertEquals(FlowEventTypes.FLOW_INSTANCE_STARTED, response.getEnvelope().getRoutingKey());

      JsonNode body = MAPPER.readTree(response.getBody());
      assertEquals(projectId, body.get("projectId").asText());
      assertEquals(flowDeployId, body.get("flowDeployId").asText());
      assertEquals(flowInstanceId, body.get("flowInstanceId").asText());
      JsonNode vars = body.get("variables");
      assertNotNull(vars, "variables snapshot should be present in payload");
      assertEquals(100, vars.get("amount").asInt());
      assertTrue(vars.get("approved").asBoolean(), "approved variable should be true");
    }
  }

  private static Connection newConnection() throws Exception {
    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost(RabbitMqTestResource.host);
    factory.setPort(RabbitMqTestResource.port);
    factory.setUsername(RabbitMqTestResource.username);
    factory.setPassword(RabbitMqTestResource.password);
    return factory.newConnection();
  }

  /**
   * 有界轮询拉取消息：桥接转发为异步，需留足时间。
   */
  private static GetResponse pollForMessage(Channel channel, String queue, long timeoutMs)
    throws Exception {
    long deadline = System.currentTimeMillis() + timeoutMs;
    GetResponse response = channel.basicGet(queue, true);
    while (response == null && System.currentTimeMillis() < deadline) {
      sleep(100L);
      response = channel.basicGet(queue, true);
    }
    return response;
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
