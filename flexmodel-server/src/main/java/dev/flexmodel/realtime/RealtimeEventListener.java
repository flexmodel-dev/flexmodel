package dev.flexmodel.realtime;

import dev.flexmodel.event.ChangedEvent;
import dev.flexmodel.event.EventListener;
import dev.flexmodel.event.PreChangeEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

/**
 * 实时事件监听器，桥接引擎层的 EventPublisher 到 RealtimeBroadcaster。
 * <p>
 * 只监听后置事件（INSERTED / UPDATED / DELETED），且仅在操作成功时广播。
 *
 * @author cjbi
 */
@Slf4j
@ApplicationScoped
public class RealtimeEventListener implements EventListener {

  @Inject
  RealtimeBroadcaster broadcaster;

  @Override
  public void onChanged(ChangedEvent event) {
    if (!event.isSuccess()) {
      return;
    }
    try {
      broadcaster.broadcast(event);
    } catch (Exception e) {
      log.error("Failed to broadcast realtime event: schema={}, model={}, eventType={}",
        event.getSchemaName(), event.getModelName(), event.getEventType(), e);
    }
  }

  @Override
  public boolean supports(String eventType) {
    return "INSERTED".equals(eventType)
      || "UPDATED".equals(eventType)
      || "DELETED".equals(eventType);
  }

  @Override
  public int getOrder() {
    return 1000; // 低优先级，不影响业务逻辑
  }
}
