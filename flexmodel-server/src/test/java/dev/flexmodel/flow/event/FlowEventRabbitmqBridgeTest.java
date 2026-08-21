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
 * 验证 FlowEventRabbitmqBridge 的可选桥接语义：默认通道禁用且不连 broker 时，
 * 桥接 bean 仍可创建，本地事件照常经 FlowEventPublisher 广播。
 * 不依赖真实 broker，亦不依赖 SmallRye InMemoryConnector（当前 Quarkus 版本未提供配套扩展），
 * 仅以默认配置启动应用即可断言桥接的零侵入设计目标。
 *
 * @author cjbi
 */
@QuarkusTest
@QuarkusTestResource(SQLiteTestResource.class)
public class FlowEventRabbitmqBridgeTest {

  @Inject
  FlowEventRabbitmqBridge bridge;

  @Inject
  FlowEventPublisher flowEventPublisher;

  @Inject
  FlowEventTestConsumer capturingConsumer;

  @Test
  void bridgeBeanPresentAndNonThrowingByDefault() {
    // 默认通道禁用：桥接 bean 仍存在（零侵入），发布桥接也消费的事件不应抛出，
    // 且不连 broker（应用无 broker 启动即证）
    assertNotNull(bridge, "bridge bean should be present (zero-intrusion)");
    Map<String, Object> variables = new HashMap<>();
    variables.put("k", "v");
    flowEventPublisher.publish(new FlowInstanceStartedEvent("proj-bridge", "caller-bridge",
      "deploy-bridge", "instance-bridge", variables));
  }

  @Test
  void disabledBridgeIsTransparentToLocalEvents() {
    capturingConsumer.captured().set(null);

    Map<String, Object> variables = new HashMap<>();
    variables.put("k", "v");
    flowEventPublisher.publish(new FlowInstanceStartedEvent("proj-bridge", "caller-bridge",
      "deploy-bridge", "instance-bridge", variables));

    FlowInstanceStartedEvent event = pollFor(capturingConsumer.captured(), 5000L);

    assertNotNull(event, "local EventBus consumer should still receive event while channel disabled");
    assertEquals("proj-bridge", event.getProjectId());
    assertEquals("instance-bridge", event.getFlowInstanceId());
    assertTrue(event.getTimestamp() > 0);
  }

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
