package dev.flexmodel.flow.event;

import dev.flexmodel.SQLiteTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 {@link FlowEventPublisher} 将强类型事件经 Vert.x EventBus 广播，
 * 且事件字段完整保留。本地事件为机制本身，不依赖 RabbitMQ。
 *
 * @author cjbi
 */
@QuarkusTest
@QuarkusTestResource(SQLiteTestResource.class)
public class FlowEventPublisherTest {

  @Inject
  FlowEventPublisher flowEventPublisher;

  @Inject
  FlowEventTestConsumer capturingConsumer;

  @Test
  void publishFlowInstanceStartedEventFieldsPreserved() {
    capturingConsumer.captured().set(null);

    String projectId = "proj-test";
    String initiator = "caller-test";
    String flowDeployId = "deploy-test";
    String flowInstanceId = "instance-test";
    Map<String, Object> variables = new HashMap<>();
    variables.put("amount", 100);
    variables.put("approved", true);

    flowEventPublisher.publish(new FlowInstanceStartedEvent(projectId, initiator, flowDeployId,
      flowInstanceId, variables));

    FlowInstanceStartedEvent event = pollFor(capturingConsumer.captured(), 5000L);

    assertNotNull(event, "FlowInstanceStartedEvent should be received via EventBus");
    assertEquals(projectId, event.getProjectId());
    assertEquals(initiator, event.getInitiator());
    assertEquals(flowDeployId, event.getFlowDeployId());
    assertEquals(flowInstanceId, event.getFlowInstanceId());
    assertNotNull(event.getVariables());
    assertEquals(100, event.getVariables().get("amount"));
    assertEquals(Boolean.TRUE, event.getVariables().get("approved"));
    assertEquals(FlowEventTypes.FLOW_INSTANCE_STARTED, event.routingKey());
    assertTrue(event.getTimestamp() > 0, "timestamp should be set at construction");
  }

  @Test
  void publishNullEventIsNoOp() {
    // 不应抛出，也不应影响其他消费者
    flowEventPublisher.publish(null);
  }

  /**
   * 有界轮询等待消费者捕获事件，避免引入 Awaitility 依赖。
   */
  private static <T> T pollFor(AtomicReference<T> ref, long timeoutMs) {
    long deadline = System.currentTimeMillis() + timeoutMs;
    T value = ref.get();
    while (value == null && System.currentTimeMillis() < deadline) {
      sleep(50L);
      value = ref.get();
    }
    return value;
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
