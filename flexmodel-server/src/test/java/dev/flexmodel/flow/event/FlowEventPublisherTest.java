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
 * 楠岃瘉 {@link FlowEventPublisher} 灏嗗己绫诲瀷浜嬩欢缁?Vert.x EventBus 骞挎挱锛?
 * 涓斾簨浠跺瓧娈靛畬鏁翠繚鐣欍€傛湰鍦颁簨浠朵负鏈哄埗鏈韩锛屼笉渚濊禆 RabbitMQ銆?
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
    String caller = "caller-test";
    String flowDeployId = "deploy-test";
    String flowInstanceId = "instance-test";
    Map<String, Object> variables = new HashMap<>();
    variables.put("amount", 100);
    variables.put("approved", true);

    flowEventPublisher.publish(new FlowInstanceStartedEvent(projectId, caller, flowDeployId,
      flowInstanceId, variables));

    FlowInstanceStartedEvent event = pollFor(capturingConsumer.captured(), 5000L);

    assertNotNull(event, "FlowInstanceStartedEvent should be received via EventBus");
    assertEquals(projectId, event.getProjectId());
    assertEquals(caller, event.getCaller());
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
    // 涓嶅簲鎶涘嚭锛屼篃涓嶅簲褰卞搷鍏朵粬娑堣垂鑰?
    flowEventPublisher.publish(null);
  }

  /**
   * 鏈夌晫杞绛夊緟娑堣垂鑰呮崟鑾蜂簨浠讹紝閬垮厤寮曞叆 Awaitility 渚濊禆銆?
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
