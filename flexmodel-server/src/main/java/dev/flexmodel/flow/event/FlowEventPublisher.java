package dev.flexmodel.flow.event;

import dev.flexmodel.common.messaging.ReliableMessages;
import io.vertx.mutiny.core.eventbus.EventBus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Flow 生命周期事件本地发布器。
 * <p>
 * Outbox 与节点状态同事务保存，可靠消费者成功后才确认。
 * 提交后的 Vert.x EventBus 广播保留给兼容通知，不承担业务可靠投递。
 *
 * @author cjbi
 */
@ApplicationScoped
public class FlowEventPublisher {

  private static final Logger LOGGER = LoggerFactory.getLogger(FlowEventPublisher.class);

  @Inject
  EventBus eventBus;

  @Inject
  ReliableMessages messages;

  /**
   * 发布 Flow 生命周期事件。Outbox 保存失败必须回滚执行事务。
   *
   * @param event 不可为空的事件
   */
  public void publish(FlowEvent event) {
    if (event == null) {
      return;
    }
    // The durable path participates in the node transaction. EventBus remains a compatibility notification tap.
    java.util.Map<String, Object> payload = dev.flexmodel.JsonUtils.convertValue(event, java.util.Map.class);
    payload.put("routingKey", event.routingKey());
    messages.enqueue(event.getProjectId(), java.util.UUID.randomUUID().toString(), "flow.event", payload);
    dev.flexmodel.common.ProjectUnitOfWork.afterCommit(() -> publishNotification(event));
  }

  private void publishNotification(FlowEvent event) {
    try {
      eventBus.publish(event.routingKey(), event);
    } catch (Exception e) {
      LOGGER.warn("publish flow event failed.||routingKey={}||event={}", event.routingKey(), event, e);
    }
  }
}
