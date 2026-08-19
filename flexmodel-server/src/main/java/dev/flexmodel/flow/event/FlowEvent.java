package dev.flexmodel.flow.event;

import java.io.Serializable;

/**
 * Flow 生命周期事件基类。
 * <p>
 * 公共字段 {@code projectId}、{@code caller}、{@code timestamp}（构造时设为当前毫秒）。
 * 抽象方法 {@link #routingKey()} 既作 Vert.x EventBus 地址，也作 RabbitMQ 转发的 routing key。
 * 本地事件始终经 {@code FlowEventPublisher} 发布到 EventBus；RabbitMQ 转发可选。
 *
 * @author cjbi
 */
public abstract class FlowEvent implements Serializable {

  private final String projectId;
  private final String caller;
  private final long timestamp;

  protected FlowEvent(String projectId, String caller) {
    this.projectId = projectId;
    this.caller = caller;
    this.timestamp = System.currentTimeMillis();
  }

  /**
   * 路由 key：兼作 EventBus 地址与 RabbitMQ routing key。
   *
   * @return 不可为空的路由 key
   */
  public abstract String routingKey();

  public String getProjectId() {
    return projectId;
  }

  public String getCaller() {
    return caller;
  }

  public long getTimestamp() {
    return timestamp;
  }
}
