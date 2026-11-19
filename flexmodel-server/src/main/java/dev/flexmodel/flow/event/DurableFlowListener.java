package dev.flexmodel.flow.event;

import java.util.Map;

/**
 * Business modules subscribe without adding business rules to the Flow engine.
 */
public interface DurableFlowListener {
  void onEvent(String projectId, String eventId, String routingKey, Map<String, Object> event);
}
