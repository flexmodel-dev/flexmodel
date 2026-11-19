package dev.flexmodel.apps.ai;

import dev.flexmodel.codegen.entity.AppAiConversation;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.flexmodel.apps.ai.dto.AiDtos.Message;
import dev.flexmodel.common.SessionContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * AI 助手的会话记忆：按 conversationKey（robotId:conversationId）从
 * f_app_ai_conversation 装载最近的历史消息；消息持久化仍由 AppAiService 统一负责，
 * 模型对话过程中的系统提示与工具消息不落库。会话不区分应用，跨应用共用同一段历史。
 */
@ApplicationScoped
public class AppAiChatMemory implements ChatMemoryProvider {
  private static final int MAX_MESSAGES = 20;

  @Inject
  AppAiRepository aiRepository;
  @Inject
  SessionContext context;

  @Override
  public ChatMemory get(Object conversationKey) {
    MessageWindowChatMemory memory = MessageWindowChatMemory.withMaxMessages(MAX_MESSAGES);
    String[] parts = String.valueOf(conversationKey).split(":", 2);
    if (parts.length != 2) return memory;
    AppAiConversation entity = aiRepository.conversation(parts[0], context.getUserId(), parts[1]);
    if (entity == null) return memory;
    for (Message item : AiMessages.parse(entity.getMessages())) {
      if ("user".equals(item.role())) memory.add(dev.langchain4j.data.message.UserMessage.from(item.content()));
      else if ("assistant".equals(item.role())) memory.add(AiMessage.from(item.content()));
    }
    return memory;
  }
}
