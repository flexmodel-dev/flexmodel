package dev.flexmodel.flow.event;

import java.io.Serializable;

/**
 * Flow 生命周期事件基类。
 * <p>
 * 公共字段 {@code projectId}、{@code initiator}、{@code timestamp}（构造时设为当前毫秒）。
 * 抽象方法 {@link #routingKey()} 作 Vert.x EventBus 地址（静态常量，供 {@code @ConsumeEvent} 匹配）。
 * 事件经 {@code FlowEventPublisher} 发布到本地 EventBus。
 *
 * @author cjbi
 */
public abstract class FlowEvent implements Serializable {

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
