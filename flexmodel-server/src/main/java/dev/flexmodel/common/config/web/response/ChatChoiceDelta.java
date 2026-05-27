package dev.flexmodel.common.config.web.response;

public record ChatChoiceDelta(
    Integer index,
    ChatDelta delta,
    String finishReason
) {
}
