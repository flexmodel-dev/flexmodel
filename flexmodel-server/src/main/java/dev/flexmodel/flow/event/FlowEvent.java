package dev.flexmodel.flow.event;

import dev.flexmodel.common.FlexmodelEvent;
import java.io.Serializable;

/**
 * Flow 生命周期事件基类。
 * <p>
 * 公共字段 {@code projectId}、{@code initiator}、{@code timestamp}（构造时设为当前毫秒）。
 * 抽象方法 {@link #routingKey()} 作 Vert.x EventBus 地址（静态常量，供 {@code @ConsumeEvent} 匹配）。
 * {@link #rabbitmqRoutingKey()} 在 routing key 中插入 projectId，作 RabbitMQ 转发的 routing key，
 * 格式为 {@code flow.<projectId>.<suffix>}，便于消费端按项目订阅。
 * 本地事件始终经 {@code FlowEventPublisher} 发布到 EventBus；RabbitMQ 转发可选。
 *
 * @author cjbi
 */
public abstract class FlowEvent implements Serializable, FlexmodelEvent {

  private static final String FLOW_PREFIX = "flow.";

  private final String projectId;
  private final String initiator;
  private final long timestamp;

  protected FlowEvent(String projectId, String initiator) {
    this.projectId = projectId;
    this.initiator = initiator;
    this.timestamp = System.currentTimeMillis();
  }

  /**
   * EventBus 地址（静态常量），供 {@code @ConsumeEvent} 匹配。
   * <p>
   * 形如 {@code flow.instance.started}，不含 projectId。
   *
   * @return 不可为空的 EventBus 地址
   */
  public abstract String routingKey();

  /**
   * RabbitMQ routing key，在 {@link #routingKey()} 中插入 projectId。
   * <p>
   * 形如 {@code flow.<projectId>.instance.started}，便于消费端按项目通配符订阅。
   * 若 routingKey 不以 {@code flow.} 开头则原样返回（防御性兜底）。
   *
   * @return 带 projectId 的 RabbitMQ routing key
   */
  public String rabbitmqRoutingKey() {
    String key = routingKey();
    if (key.startsWith(FLOW_PREFIX)) {
      return FLOW_PREFIX + projectId + "." + key.substring(FLOW_PREFIX.length());
    }
    return key;
  }

  public String getProjectId() {
    return projectId;
  }

  public String getInitiator() {
    return initiator;
  }

  public long getTimestamp() {
    return timestamp;
  }
}
