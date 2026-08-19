package dev.flexmodel.flow.event;

import lombok.Getter;
import lombok.ToString;

/**
 * 流程定义更新事件。
 *
 * @author cjbi
 */
@Getter
@ToString(callSuper = true)
public class FlowUpdatedEvent extends FlowEvent {

  public static final String ROUTING_KEY = FlowEventTypes.FLOW_UPDATED;

  private final String flowModuleId;

  public FlowUpdatedEvent(String projectId, String caller, String flowModuleId) {
    super(projectId, caller);
    this.flowModuleId = flowModuleId;
  }

  @Override
  public String routingKey() {
    return ROUTING_KEY;
  }
}
