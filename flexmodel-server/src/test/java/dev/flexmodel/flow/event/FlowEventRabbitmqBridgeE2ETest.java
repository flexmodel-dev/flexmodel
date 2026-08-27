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
import static org.junit.jupiter.api.Assertions.assertFalse;
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

  private static final String EXCHANGE = "flexmodel.events";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Inject
  FlowEventPublisher flowEventPublisher;

  /**
   * 验证带 variables 快照的 FlowInstanceStartedEvent 经桥接转发后，variables 仍在 JSON 载荷中完整。
   */
  @Test
  void flowInstanceStartedEventWithVariablesForwarded() throws Exception {
    String projectId = "proj-vars";
    String initiator = "caller-vars";
    String flowDeployId = "deploy-vars";
    String flowInstanceId = "instance-vars";
    Map<String, Object> variables = new HashMap<>();
    variables.put("amount", 100);
    variables.put("approved", true);

    try (Connection connection = newConnection();
         Channel channel = connection.createChannel()) {
      channel.exchangeDeclare(EXCHANGE, BuiltinExchangeType.TOPIC, true);
      String queue = channel.queueDeclare().getQueue();
      channel.queueBind(queue, EXCHANGE, "flow.proj-vars.instance.started");

      flowEventPublisher.publish(
        new FlowInstanceStartedEvent(projectId, initiator, flowDeployId, flowInstanceId, variables));

      GetResponse response = pollForMessage(channel, queue, 15000L);
      assertNotNull(response, "FlowInstanceStartedEvent should be forwarded to broker within timeout");

      assertEquals("flow.proj-vars.instance.started", response.getEnvelope().getRoutingKey());

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

  /**
   * 验证 UserTaskSuspendedEvent 经桥接转发后，routing key 正确，且 nodeAttributes（节点定义扩展属性快照）
   * 与 variables 一样在 JSON 载荷中完整保留，外部订阅者无需回查定义仓库即可读取节点配置。
   */
  @Test
  void userTaskSuspendedEventWithNodeAttributesForwarded() throws Exception {
    String projectId = "proj-task";
    String initiator = "caller-task";
    String flowDeployId = "deploy-task";
    String flowInstanceId = "instance-task";
    String nodeInstanceId = "nodeinst-task";
    String nodeKey = "approveNode";
    Map<String, Object> variables = new HashMap<>();
    variables.put("amount", 500);
    Map<String, Object> nodeAttributes = new HashMap<>();
    nodeAttributes.put("name", "审批");
    nodeAttributes.put("assignee", "manager");
    nodeAttributes.put("multiInstance", false);

    try (Connection connection = newConnection();
         Channel channel = connection.createChannel()) {
      channel.exchangeDeclare(EXCHANGE, BuiltinExchangeType.TOPIC, true);
      String queue = channel.queueDeclare().getQueue();
      channel.queueBind(queue, EXCHANGE, "flow.proj-task.usertask.suspended");

      flowEventPublisher.publish(new UserTaskSuspendedEvent(projectId, initiator, flowDeployId,
        flowInstanceId, nodeInstanceId, nodeKey, variables, nodeAttributes));

      GetResponse response = pollForMessage(channel, queue, 15000L);
      assertNotNull(response, "UserTaskSuspendedEvent should be forwarded to broker within timeout");

      assertEquals("flow.proj-task.usertask.suspended", response.getEnvelope().getRoutingKey());

      JsonNode body = MAPPER.readTree(response.getBody());
      assertEquals(projectId, body.get("projectId").asText());
      assertEquals(flowDeployId, body.get("flowDeployId").asText());
      assertEquals(flowInstanceId, body.get("flowInstanceId").asText());
      assertEquals(nodeInstanceId, body.get("nodeInstanceId").asText());
      assertEquals(nodeKey, body.get("nodeKey").asText());
      JsonNode vars = body.get("variables");
      assertNotNull(vars, "variables snapshot should be present");
      assertEquals(500, vars.get("amount").asInt());
      JsonNode attrs = body.get("nodeAttributes");
      assertNotNull(attrs, "nodeAttributes snapshot should be present in payload");
      assertEquals("审批", attrs.get("name").asText());
      assertEquals("manager", attrs.get("assignee").asText());
      assertFalse(attrs.get("multiInstance").asBoolean(), "multiInstance should be false");
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
