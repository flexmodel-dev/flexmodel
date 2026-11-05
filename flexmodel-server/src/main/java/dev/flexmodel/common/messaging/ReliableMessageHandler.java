package dev.flexmodel.common.messaging;

import java.util.Map;

/**
 * Executed inside the project's transaction. Throwing leaves the message available for retry.
 */
public interface ReliableMessageHandler {
  String type();

  void handle(String projectId, String messageId, Map<String, Object> payload);
}
