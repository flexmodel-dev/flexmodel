package dev.flexmodel.flow.event;

import lombok.Getter;
import lombok.ToString;

/**
 * 流程实例失败事件。
 *
 * @author cjbi
 */
@Getter
@ToString(callSuper = true)
public class FlowInstanceFailedEvent extends FlowEvent {

  public static final String ROUTING_KEY = FlowEventTypes.FLOW_INSTANCE_FAILED;

  private final String flowDeployId;
  private final String flowInstanceId;
  private final String error;

  public FlowInstanceFailedEvent(String projectId, String initiator, String flowDeployId,
                                 String flowInstanceId, String error) {
    super(projectId, initiator);
    this.flowDeployId = flowDeployId;
    this.flowInstanceId = flowInstanceId;
    this.error = error;
  }

  @Override
  public String routingKey() {
    return ROUTING_KEY;
  }
}
