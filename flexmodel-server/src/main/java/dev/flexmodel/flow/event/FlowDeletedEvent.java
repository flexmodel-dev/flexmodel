package dev.flexmodel.flow.event;

import lombok.Getter;
import lombok.ToString;

/**
 * 流程定义删除事件。
 *
 * @author cjbi
 */
@Getter
@ToString(callSuper = true)
public class FlowDeletedEvent extends FlowEvent {

  public static final String ROUTING_KEY = FlowEventTypes.FLOW_DELETED;

  private final String flowModuleId;

  public FlowDeletedEvent(String projectId, String caller, String flowModuleId) {
    super(projectId, caller);
    this.flowModuleId = flowModuleId;
  }

  @Override
  public String routingKey() {
    return ROUTING_KEY;
  }
}
