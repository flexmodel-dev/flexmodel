package dev.flexmodel.flow.event;

import io.quarkus.vertx.ConsumeEvent;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 测试用 EventBus 消费者：捕获 {@link FlowInstanceStartedEvent}，验证字段经 EventBus 透传完整。
 * <p>
 * 顶层 {@code @ApplicationScoped} Bean，确保 Quarkus 发现并注册其 {@code @ConsumeEvent} 消费者。
 *
 * @author cjbi
 */
@ApplicationScoped
public class FlowEventTestConsumer {

  final AtomicReference<FlowInstanceStartedEvent> captured = new AtomicReference<>();

  @ConsumeEvent(value = FlowEventTypes.FLOW_INSTANCE_STARTED, blocking = false)
  void onFlowInstanceStarted(FlowInstanceStartedEvent event) {
    captured.set(event);
  }

  public AtomicReference<FlowInstanceStartedEvent> captured() {
    return captured;
  }
}
