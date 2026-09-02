package dev.flexmodel.realtime;

import dev.flexmodel.codegen.entity.Project;
import dev.flexmodel.common.FlexmodelConfig;
import dev.flexmodel.common.FlexmodelEvent;
import dev.flexmodel.event.ChangedEvent;
import dev.flexmodel.event.EventListener;
import dev.flexmodel.project.ProjectRepository;
import io.smallrye.reactive.messaging.MutinyEmitter;
import io.smallrye.reactive.messaging.rabbitmq.OutgoingRabbitMQMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Metadata;

import java.util.Map;

/**
 * 数据变更事件 RabbitMQ 监听器，桥接引擎层的 EventPublisher 到 RabbitMQ topic 交换机。
 * <p>
 * 参照 {@link RealtimeEventListener} 的模式：监听后置事件（INSERTED / UPDATED / DELETED），
 * 仅在操作成功时转发。与 WebSocket 实时广播互不影响，各走各的通道。
 * <p>
 * routing key 形如 {@code data.<projectId>.<modelName>.<operation>}，投递到与 flow 事件共用的
 * {@code flexmodel.events} topic 交换机，消费端可按项目、模型或操作类型订阅。
 * <p>
 * 投递为尽力而为：失败仅告警，不重试、不阻塞、不回滚。
 *
 * @author cjbi
 */
@Slf4j
@ApplicationScoped
public class RealtimeRabbitmqListener implements EventListener {

  @Inject
  FlexmodelConfig flexmodelConfig;

  @Inject
  @Channel("events-out")
  Instance<MutinyEmitter<FlexmodelEvent>> dataEventEmitterInstance;

  @Inject
  ProjectRepository projectRepository;

  @Override
  public void onChanged(ChangedEvent event) {
    if (!flexmodelConfig.events().rabbitmq().enabled()) {
      return;
    }
    // 操作失败时不转发，避免无谓的对象构造与 IO
    if (!event.isSuccess()) {
      return;
    }
    if (dataEventEmitterInstance.isUnsatisfied()) {
      log.debug("events-out channel not resolvable, skip forwarding.||model={}", event.getModelName());
      return;
    }
    try {
      Project project = projectRepository.findProjectByDatabaseName(event.getSchemaName());
      String operation = mapEventType(event.getEventType());
      String routingKey = "data." + project.getId() + "." + event.getModelName() + "." + operation.toLowerCase();
      Map<String, Object> newData = event.getNewData() != null ? event.getNewData() : Map.of();
      Map<String, Object> oldData = event.getOldData() != null ? event.getOldData() : Map.of();

      DataChangeEvent payload = new DataChangeEvent(
        routingKey, project.getId(), operation, event.getModelName(), event.getSchemaName(),
        event.getId(), event.getTimestamp(), event.getAffectedRows(), newData, oldData);

      MutinyEmitter<FlexmodelEvent> emitter = dataEventEmitterInstance.get();
      OutgoingRabbitMQMetadata metadata = new OutgoingRabbitMQMetadata.Builder()
        .withRoutingKey(routingKey)
        // 持久化投递（delivery_mode=2）：使消息在 broker 重启后仍可恢复，配合 durable 交换机与 durable 队列生效
        .withDeliveryMode(2)
        .build();
      // 异步发送，不阻塞引擎写入线程；失败仅告警
      emitter.sendMessage(Message.of(payload, Metadata.of(metadata)))
        .onFailure()
        .invoke(e -> log.warn("forward data change event to rabbitmq failed.||model={}||operation={}",
          event.getModelName(), operation, e))
        .subscribe()
        .asCompletionStage();
    } catch (Exception e) {
      log.warn("forward data change event to rabbitmq failed (sync).||model={}", event.getModelName(), e);
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
    // 低于 RealtimeEventListener(1000)，不影响业务实时推送
    return 1100;
  }

  /**
   * 引擎事件类型映射到协议操作类型：INSERTED -> INSERT, UPDATED -> UPDATE, DELETED -> DELETE
   */
  private String mapEventType(String engineEventType) {
    return switch (engineEventType) {
      case "INSERTED" -> "INSERT";
      case "UPDATED" -> "UPDATE";
      case "DELETED" -> "DELETE";
      default -> engineEventType;
    };
  }
}
