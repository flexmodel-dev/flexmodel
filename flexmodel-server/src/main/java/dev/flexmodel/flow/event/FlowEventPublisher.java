package dev.flexmodel.flow.event;

import io.vertx.mutiny.core.eventbus.EventBus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Flow 生命周期事件本地发布器。
 * <p>
 * 始终将事件发布到 Vert.x EventBus（本地事件为机制本身），不与 RabbitMQ 配置耦合。
 * 全程 try/catch 仅 {@code LOGGER.warn}，不抛出、不阻塞流程。
 *
 * @author cjbi
 */
@ApplicationScoped
public class FlowEventPublisher {

  private static final Logger LOGGER = LoggerFactory.getLogger(FlowEventPublisher.class);

  @Inject
  EventBus eventBus;

  /**
   * 发布 Flow 生命周期事件。失败仅告警，不影响流程主链路。
   *
   * @param event 不可为空的事件
   */
  public void publish(FlowEvent event) {
    if (event == null) {
      return;
    }
    try {
      eventBus.publish(event.routingKey(), event);
    } catch (Exception e) {
      LOGGER.warn("publish flow event failed.||routingKey={}||event={}", event.routingKey(), event, e);
    }
  }
}
