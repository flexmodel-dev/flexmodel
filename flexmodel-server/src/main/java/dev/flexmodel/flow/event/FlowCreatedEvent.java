package dev.flexmodel.flow.event;

import lombok.Getter;
import lombok.ToString;

/**
 * 流程定义创建事件。
 *
 * @author cjbi
 */
@Getter
@ToString(callSuper = true)
public class FlowCreatedEvent extends FlowEvent {

  public static final String ROUTING_KEY = FlowEventTypes.FLOW_CREATED;

  private final String flowModuleId;
  private final String flowKey;

  public FlowCreatedEvent(String projectId, String caller, String flowModuleId, String flowKey) {
    super(projectId, caller);
    this.flowModuleId = flowModuleId;
    this.flowKey = flowKey;
  }

  @Override
  public String routingKey() {
    return ROUTING_KEY;
  }
}
