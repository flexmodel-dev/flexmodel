package dev.flexmodel.flow.event;

/**
 * Flow 生命周期事件路由 key 常量。
 * <p>
 * 供 {@code @ConsumeEvent} 注解与 RabbitMQ 桥接消费者引用，避免散落字符串字面量。
 *
 * @author cjbi
 */
public final class FlowEventTypes {

  private FlowEventTypes() {
  }

  // 定义层
  public static final String FLOW_CREATED = "flow.created";
  public static final String FLOW_UPDATED = "flow.updated";
  public static final String FLOW_DEPLOYED = "flow.deployed";
  public static final String FLOW_DELETED = "flow.deleted";

  // 实例层
  public static final String FLOW_INSTANCE_STARTED = "flow.instance.started";
  public static final String FLOW_INSTANCE_COMPLETED = "flow.instance.completed";
  public static final String FLOW_INSTANCE_FAILED = "flow.instance.failed";
  public static final String FLOW_INSTANCE_TERMINATED = "flow.instance.terminated";

  // 用户任务层
  public static final String USER_TASK_SUSPENDED = "flow.usertask.suspended";
  public static final String USER_TASK_COMMITTED = "flow.usertask.committed";
  public static final String USER_TASK_ROLLBACK_SUSPENDED = "flow.usertask.rollback.suspended";
}
