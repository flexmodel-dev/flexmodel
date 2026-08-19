package dev.flexmodel.flow.event;

import io.quarkus.vertx.ConsumeEvent;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 娴嬭瘯鐢?EventBus 娑堣垂鑰咃細鎹曡幏 {@link FlowInstanceStartedEvent}锛岄獙璇佸瓧娈电粡 EventBus 閫忎紶瀹屾暣銆?
 * <p>
 * 椤跺眰 {@code @ApplicationScoped} Bean锛岀‘淇?Quarkus 鍙戠幇骞舵敞鍐屽叾 {@code @ConsumeEvent} 娑堣垂鑰呫€?
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
