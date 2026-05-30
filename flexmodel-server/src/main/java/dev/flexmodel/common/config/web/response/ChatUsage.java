package dev.flexmodel.common.config.web.response;

public record ChatUsage(
    Integer promptTokens,
    Integer completionTokens,
    Integer totalTokens
) {
}
