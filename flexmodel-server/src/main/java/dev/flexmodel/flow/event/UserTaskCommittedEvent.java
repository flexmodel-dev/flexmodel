package dev.flexmodel.flow.event;

import lombok.Getter;
import lombok.ToString;

import java.util.Map;

/**
 * 用户任务提交完成事件。
 *
 * @author cjbi
 */
@Getter
@ToString(callSuper = true)
public class UserTaskCommittedEvent extends FlowEvent {

  public static final String ROUTING_KEY = FlowEventTypes.USER_TASK_COMMITTED;

  private final String flowDeployId;
  private final String flowInstanceId;
  private final String nodeInstanceId;
  private final String nodeKey;
  private final Map<String, Object> nodeAttributes;

  public UserTaskCommittedEvent(String projectId, String caller, String flowDeployId, String flowInstanceId,
                                String nodeInstanceId, String nodeKey, Map<String, Object> nodeAttributes) {
    super(projectId, caller);
    this.flowDeployId = flowDeployId;
    this.flowInstanceId = flowInstanceId;
    this.nodeInstanceId = nodeInstanceId;
    this.nodeKey = nodeKey;
    this.nodeAttributes = nodeAttributes == null ? null : new java.util.HashMap<>(nodeAttributes);
  }

  @Override
  public String routingKey() {
    return ROUTING_KEY;
  }
}
