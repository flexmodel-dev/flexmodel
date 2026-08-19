package dev.flexmodel.flow.event;

import io.smallrye.reactive.messaging.MutinyEmitter;
import io.smallrye.reactive.messaging.rabbitmq.OutgoingRabbitMQMetadata;
import io.quarkus.vertx.ConsumeEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Metadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Flow 生命周期事件 RabbitMQ 桥接。
 * <p>
 * 订阅全部内部 EventBus 事件并转发到单个 topic 交换机（routing key 复用事件地址）。
 * 是否连 broker 由 SmallRye 通道 {@code mp.messaging.outgoing.flow-events-out.enabled} 单一控制：
 * 默认关闭时 SmallRye 注入 no-op emitter，{@link #forward(FlowEvent)} 发送即丢弃、不连 broker；
 * 本地事件照常经 {@link FlowEventPublisher} 发布。启用需置该通道 {@code enabled=true} 并提供 broker 连接配置。
 * <p>
 * 每个事件一个 {@code @ConsumeEvent} 方法、消费具体类型，沿用代码库已验证的同类型发布/消费模式，
 * 避免多态反序列化不确定性。
 * <p>
 * 使用 {@link Instance} 延迟注入 {@link MutinyEmitter}：通道未启用时 SmallRye 提供 no-op emitter，
 * bean 仍可正常创建，不影响本地事件广播。
 *
 * @author cjbi
 */
@ApplicationScoped
public class FlowEventRabbitmqBridge {

  private static final Logger LOGGER = LoggerFactory.getLogger(FlowEventRabbitmqBridge.class);

  @Inject
  @Channel("flow-events-out")
  Instance<MutinyEmitter<FlowEvent>> flowEventEmitterInstance;

  // 通道是否启用；默认关闭，禁用时 SmallRye 注入 no-op emitter，转发静默跳过、不连 broker
  @ConfigProperty(name = "mp.messaging.outgoing.flow-events-out.enabled", defaultValue = "false")
  boolean channelEnabled;

  @ConsumeEvent(value = FlowEventTypes.FLOW_CREATED, blocking = false)
  public void onFlowCreated(FlowCreatedEvent event) {
    forward(event);
  }

  @ConsumeEvent(value = FlowEventTypes.FLOW_UPDATED, blocking = false)
  public void onFlowUpdated(FlowUpdatedEvent event) {
    forward(event);
  }

  @ConsumeEvent(value = FlowEventTypes.FLOW_DEPLOYED, blocking = false)
  public void onFlowDeployed(FlowDeployedEvent event) {
    forward(event);
  }

  @ConsumeEvent(value = FlowEventTypes.FLOW_DELETED, blocking = false)
  public void onFlowDeleted(FlowDeletedEvent event) {
    forward(event);
  }

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
    // 通道未启用时静默跳过：不连 broker、不打日志，保证默认配置零侵入
    if (!channelEnabled) {
      return;
    }
    if (flowEventEmitterInstance.isUnsatisfied()) {
      LOGGER.debug("flow-events-out channel not resolvable, skip forwarding.||routingKey={}", event.routingKey());
      return;
    }
    try {
      MutinyEmitter<FlowEvent> emitter = flowEventEmitterInstance.get();
      OutgoingRabbitMQMetadata metadata = new OutgoingRabbitMQMetadata.Builder()
        .withRoutingKey(event.routingKey())
        .build();
      emitter.sendMessage(Message.of(event, Metadata.of(metadata)))
        .onFailure()
        .invoke(e -> LOGGER.warn("forward flow event to rabbitmq failed.||routingKey={}||payloadType={}",
          event.routingKey(), event.getClass().getSimpleName(), e))
        .subscribe()
        .asCompletionStage();
    } catch (Exception e) {
      // 仅记录 routing key 与异常，不打印事件载荷（避免泄露流程变量）
      LOGGER.warn("forward flow event to rabbitmq failed (sync).||routingKey={}", event.routingKey(), e);
    }
  }
}
