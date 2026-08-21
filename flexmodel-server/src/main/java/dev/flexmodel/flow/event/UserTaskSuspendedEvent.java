package dev.flexmodel.flow.event;

import lombok.Getter;
import lombok.ToString;

import java.util.Map;

/**
 * 用户任务挂起事件。
 *
 * @author cjbi
 */
@Getter
@ToString(callSuper = true)
public class UserTaskSuspendedEvent extends FlowEvent {

  public static final String ROUTING_KEY = FlowEventTypes.USER_TASK_SUSPENDED;

  private final String flowDeployId;
  private final String flowInstanceId;
  private final String nodeInstanceId;
  private final String nodeKey;
  private final Map<String, Object> variables;
  private final Map<String, Object> nodeAttributes;

  public UserTaskSuspendedEvent(String projectId, String caller, String flowDeployId, String flowInstanceId,
                                String nodeInstanceId, String nodeKey, Map<String, Object> variables,
                                Map<String, Object> nodeAttributes) {
    super(projectId, caller);
    this.flowDeployId = flowDeployId;
    this.flowInstanceId = flowInstanceId;
    this.nodeInstanceId = nodeInstanceId;
    this.nodeKey = nodeKey;
    this.variables = variables == null ? null : new java.util.HashMap<>(variables);
    this.nodeAttributes = nodeAttributes == null ? null : new java.util.HashMap<>(nodeAttributes);
  }

  @Override
  public String routingKey() {
    return ROUTING_KEY;
  }
}
