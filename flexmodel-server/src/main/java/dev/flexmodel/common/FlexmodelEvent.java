package dev.flexmodel.common;

/**
 * 所有经 SmallRye RabbitMQ 通道 {@code events-out} 投递到 {@code flexmodel.events} topic 交换机的事件载荷标记接口。
 * <p>
 * Flow 生命周期事件（{@code dev.flexmodel.flow.event.FlowEvent} 及子类）与数据变更事件
 * （{@code dev.flexmodel.realtime.DataChangeEvent}）共享单一出站通道，消费端按 routing key 区分流类型。
 *
 * @author cjbi
 */
public interface FlexmodelEvent {
}
