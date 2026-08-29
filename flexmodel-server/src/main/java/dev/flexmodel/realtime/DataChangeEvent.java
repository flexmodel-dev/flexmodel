package dev.flexmodel.realtime;

import dev.flexmodel.common.FlexmodelEvent;
import java.io.Serializable;
import java.util.Map;

/**
 * 数据变更事件载荷，由 {@link RealtimeRabbitmqListener} 从引擎 {@code ChangedEvent} 转换而来，
 * 经 SmallRye RabbitMQ 通道 {@code events-out} 投递到 {@code flexmodel.events} topic 交换机。
 * <p>
 * routing key 形如 {@code data.<projectId>.<modelName>.<operation>}，便于消费端按项目、模型或操作类型订阅过滤。
 *
 * @author cjbi
 */
public class DataChangeEvent implements Serializable, FlexmodelEvent {

  private final String routingKey;
  private final String projectId;
  private final String event;
  private final String model;
  private final String schema;
  private final Object recordId;
  private final long timestamp;
  private final int affectedRows;
  private final Map<String, Object> data;
  private final Map<String, Object> oldData;

  public DataChangeEvent(String routingKey, String projectId, String event, String model, String schema,
                         Object recordId, long timestamp, int affectedRows,
                         Map<String, Object> data, Map<String, Object> oldData) {
    this.routingKey = routingKey;
    this.projectId = projectId;
    this.event = event;
    this.model = model;
    this.schema = schema;
    this.recordId = recordId;
    this.timestamp = timestamp;
    this.affectedRows = affectedRows;
    this.data = data;
    this.oldData = oldData;
  }

  public String getRoutingKey() {
    return routingKey;
  }

  public String getProjectId() {
    return projectId;
  }

  public String getEvent() {
    return event;
  }

  public String getModel() {
    return model;
  }

  public String getSchema() {
    return schema;
  }

  public Object getRecordId() {
    return recordId;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public int getAffectedRows() {
    return affectedRows;
  }

  public Map<String, Object> getData() {
    return data;
  }

  public Map<String, Object> getOldData() {
    return oldData;
  }
}
