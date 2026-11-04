package dev.flexmodel.apps.ai;

import dev.langchain4j.model.chat.DisabledStreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import java.time.Duration;

/**
 * 自定义流式模型 bean：与 quarkus-langchain4j openai 扩展使用同一套配置，
 * 仅额外开启 returnThinking，使 OpenAI 兼容供应商（如 DeepSeek reasoner）的
 * reasoning_content 解析为 {@code ChatEvent.PartialThinkingEvent}。
 * 扩展注册的模型 bean 为 defaultBean，本 bean 存在时自动替换，不影响密钥、URL、超时配置来源。
 * sendThinking 保持默认关闭：推理内容不回传供应商，避免其拒绝携带 reasoning 的请求。
 */
@ApplicationScoped
public class AiStreamingModelProducer {
  private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";

  // 与扩展默认装配一致，注入全部 ChatModelListener，保留指标与追踪能力。
  @Any
  @Inject
  Instance<ChatModelListener> listeners;

  @Produces
  public StreamingChatModel streamingChatModel() {
    Config config = ConfigProvider.getConfig();
    String apiKey = config.getOptionalValue("quarkus.langchain4j.openai.api-key", String.class).orElse("");
    // 扩展在未设置 api-key 时默认填充 "dummy"，同样视为未配置。
    if (apiKey.isBlank() || "dummy".equals(apiKey)) return new DisabledStreamingChatModel();
    return OpenAiStreamingChatModel.builder()
      .baseUrl(config.getOptionalValue("quarkus.langchain4j.openai.base-url", String.class).orElse(DEFAULT_BASE_URL))
      .apiKey(apiKey)
      .modelName(config.getOptionalValue("quarkus.langchain4j.openai.chat-model.model-name", String.class)
        .orElse("gpt-4o-mini"))
      .temperature(config.getOptionalValue("quarkus.langchain4j.openai.chat-model.temperature", Double.class)
        .orElse(null))
      .timeout(config.getOptionalValue("quarkus.langchain4j.openai.timeout", Duration.class)
        .orElse(Duration.ofSeconds(60)))
      .logRequests(config.getOptionalValue("quarkus.langchain4j.log-requests", Boolean.class).orElse(false))
      .logResponses(config.getOptionalValue("quarkus.langchain4j.log-responses", Boolean.class).orElse(false))
      .listeners(listeners.stream().toList())
      .returnThinking(true)
      .build();
  }
}
