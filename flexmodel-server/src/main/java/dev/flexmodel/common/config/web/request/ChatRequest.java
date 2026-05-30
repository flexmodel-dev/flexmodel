package dev.flexmodel.common.config.web.request;

import dev.flexmodel.common.config.web.response.ChatMessage;

import java.util.List;

public record ChatRequest(
  String conversationId,
  String model,
  List<ChatMessage> messages,
  Double temperature,
  Integer maxTokens
) {
}
