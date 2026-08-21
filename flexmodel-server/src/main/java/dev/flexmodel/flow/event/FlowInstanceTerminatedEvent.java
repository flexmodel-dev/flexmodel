package dev.flexmodel.flow.event;

import lombok.Getter;
import lombok.ToString;

/**
 * 流程实例终止事件。
 *
 * @author cjbi
 */
@Getter
@ToString(callSuper = true)
public class FlowInstanceTerminatedEvent extends FlowEvent {

  public static final String ROUTING_KEY = FlowEventTypes.FLOW_INSTANCE_TERMINATED;

  private final String flowInstanceId;

  public FlowInstanceTerminatedEvent(String projectId, String caller, String flowInstanceId) {
    super(projectId, caller);
    this.flowInstanceId = flowInstanceId;
  }

  @Override
  public String routingKey() {
    return ROUTING_KEY;
  }
}
