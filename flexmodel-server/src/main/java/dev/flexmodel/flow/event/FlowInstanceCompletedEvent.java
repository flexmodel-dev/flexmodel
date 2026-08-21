package dev.flexmodel.flow.event;

import lombok.Getter;
import lombok.ToString;

import java.util.Map;

/**
 * 流程实例完成事件。
 *
 * @author cjbi
 */
@Getter
@ToString(callSuper = true)
public class FlowInstanceCompletedEvent extends FlowEvent {

  public static final String ROUTING_KEY = FlowEventTypes.FLOW_INSTANCE_COMPLETED;

  private final String flowDeployId;
  private final String flowInstanceId;
  private final Map<String, Object> variables;

  public FlowInstanceCompletedEvent(String projectId, String caller, String flowDeployId,
                                    String flowInstanceId, Map<String, Object> variables) {
    super(projectId, caller);
    this.flowDeployId = flowDeployId;
    this.flowInstanceId = flowInstanceId;
    this.variables = variables == null ? null : new java.util.HashMap<>(variables);
  }

  @Override
  public String routingKey() {
    return ROUTING_KEY;
  }
}
