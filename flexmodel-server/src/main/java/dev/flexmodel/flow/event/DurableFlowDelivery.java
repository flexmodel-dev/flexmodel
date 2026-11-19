package dev.flexmodel.flow.event;

import dev.flexmodel.common.messaging.ReliableMessageHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.Map;

@ApplicationScoped
public class DurableFlowDelivery implements ReliableMessageHandler {
  @Inject
  Instance<DurableFlowListener> listeners;

  public String type() {
    return "flow.event";
  }

  public void handle(String projectId, String eventId, Map<String, Object> payload) {
    for (DurableFlowListener listener : listeners)
      listener.onEvent(projectId, eventId, String.valueOf(payload.get("routingKey")), payload);
  }
}
