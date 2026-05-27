package dev.flexmodel.common.config.web.response;

import java.util.List;

public record ChatResponseDelta(
  String object,
  String conversationId,
  String id,
  String created,
  String model,
    List<ChatChoiceDelta> choices,
    ChatUsage usage
) {
}
