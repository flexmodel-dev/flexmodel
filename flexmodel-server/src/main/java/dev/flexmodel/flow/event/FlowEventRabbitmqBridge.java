package dev.flexmodel.flow.event;

import dev.flexmodel.common.FlexmodelConfig;
import io.smallrye.reactive.messaging.MutinyEmitter;
import dev.flexmodel.common.FlexmodelEvent;
import io.smallrye.reactive.messaging.rabbitmq.OutgoingRabbitMQMetadata;
import io.quarkus.vertx.ConsumeEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Metadata;

/**
 * Flow 生命周期事件 RabbitMQ 桥接。
 * <p>
 * 订阅全部内部 EventBus 事件并转发到单个 topic 交换机（routing key 复用事件地址）。
 * 默认不连 broker（{@code flexmodel.events.rabbitmq.enabled=false}）：
 * 关闭时 SmallRye 注入 no-op emitter，{@link #forward(FlowEvent)} 发送即丢弃、不连 broker；
 * 本地事件照常经 {@link FlowEventPublisher} 发布。启用需置 {@code flexmodel.events.rabbitmq.enabled=true}。
 * <p>
 * 每个事件一个 {@code @ConsumeEvent} 方法、消费具体类型，沿用代码库已验证的同类型发布/消费模式，
 * 避免多态反序列化不确定性。
 * <p>
 * 使用 {@link Instance} 延迟注入 {@link MutinyEmitter}：通道未启用时 SmallRye 提供 no-op emitter，
 * bean 仍可正常创建，不影响本地事件广播。
 *
 * @author cjbi
 */
@Slf4j
@ApplicationScoped
public class FlowEventRabbitmqBridge {

  @Inject
  FlexmodelConfig flexmodelConfig;

  @Inject
  @Channel("events-out")
  Instance<MutinyEmitter<FlexmodelEvent>> flowEventEmitterInstance;

  @ConsumeEvent(value = FlowEventTypes.FLOW_INSTANCE_STARTED, blocking = false)
  public void onFlowInstanceStarted(FlowInstanceStartedEvent event) {
    forward(event);
  }

  @ConsumeEvent(value = FlowEventTypes.FLOW_INSTANCE_COMPLETED, blocking = false)
  public void onFlowInstanceCompleted(FlowInstanceCompletedEvent event) {
    forward(event);
  }

  @ConsumeEvent(value = FlowEventTypes.FLOW_INSTANCE_FAILED, blocking = false)
  public void onFlowInstanceFailed(FlowInstanceFailedEvent event) {
    forward(event);
  }

  @ConsumeEvent(value = FlowEventTypes.FLOW_INSTANCE_TERMINATED, blocking = false)
  public void onFlowInstanceTerminated(FlowInstanceTerminatedEvent event) {
    forward(event);
  }

  @ConsumeEvent(value = FlowEventTypes.USER_TASK_SUSPENDED, blocking = false)
  public void onUserTaskSuspended(UserTaskSuspendedEvent event) {
    forward(event);
  }

  @ConsumeEvent(value = FlowEventTypes.USER_TASK_COMMITTED, blocking = false)
  public void onUserTaskCommitted(UserTaskCommittedEvent event) {
    forward(event);
  }

  @ConsumeEvent(value = FlowEventTypes.USER_TASK_ROLLBACK_SUSPENDED, blocking = false)
  public void onUserTaskRollbackSuspended(UserTaskRollbackSuspendedEvent event) {
    forward(event);
  }

  /**
   * 尽力而为转发：以事件 routing key 作 RabbitMQ routing key，失败仅告警、不重试、不阻塞。
   * 通道未启用时 SmallRye 提供 no-op emitter，发送即丢弃、不连 broker。
   */
  private void forward(FlowEvent event) {
    if (!flexmodelConfig.events().rabbitmq().enabled()) {
      return;
    }
    if (flowEventEmitterInstance.isUnsatisfied()) {
      log.debug("events-out channel not resolvable, skip forwarding.||routingKey={}", event.rabbitmqRoutingKey());
      return;
    }
    try {
      MutinyEmitter<FlexmodelEvent> emitter = flowEventEmitterInstance.get();
      OutgoingRabbitMQMetadata metadata = new OutgoingRabbitMQMetadata.Builder()
        .withRoutingKey(event.rabbitmqRoutingKey())
        // 持久化投递（delivery_mode=2）：使消息在 broker 重启后仍可恢复，配合 durable 交换机与 durable 队列生效
        .withDeliveryMode(2)
        .build();
      emitter.sendMessage(Message.of(event, Metadata.of(metadata)))
        .onFailure()
        .invoke(e -> log.warn("forward flow event to rabbitmq failed.||routingKey={}||payloadType={}",
          event.rabbitmqRoutingKey(), event.getClass().getSimpleName(), e))
        .subscribe()
        .asCompletionStage();
    } catch (Exception e) {
      // 仅记录 routing key 与异常，不打印事件载荷（避免泄露流程变量）
      log.warn("forward flow event to rabbitmq failed (sync).||routingKey={}", event.rabbitmqRoutingKey(), e);
    }
  }
}
