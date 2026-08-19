package dev.flexmodel.flow.event;

import lombok.Getter;
import lombok.ToString;

/**
 * 流程定义部署事件。
 *
 * @author cjbi
 */
@Getter
@ToString(callSuper = true)
public class FlowDeployedEvent extends FlowEvent {

  public static final String ROUTING_KEY = FlowEventTypes.FLOW_DEPLOYED;

  private final String flowModuleId;
  private final String flowDeployId;

  public FlowDeployedEvent(String projectId, String caller, String flowModuleId, String flowDeployId) {
    super(projectId, caller);
    this.flowModuleId = flowModuleId;
    this.flowDeployId = flowDeployId;
  }

  @Override
  public String routingKey() {
    return ROUTING_KEY;
  }
}
