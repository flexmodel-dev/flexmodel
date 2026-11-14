package dev.flexmodel.apps.ai;

import dev.flexmodel.mcp.AppTools;
import dev.flexmodel.mcp.DirectoryTools;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.runtime.aiservice.ChatEvent;
import io.smallrye.mutiny.Multi;

/**
 * 应用 AI 助手（quarkus-langchain4j 声明式 AI Service，实现由扩展生成）。
 * <p>
 * 工具与 MCP Server 完全共用 {@link AppTools} 与 {@link DirectoryTools}：
 * 同一套 langchain4j {@code @Tool} 定义，MCP 端经
 * quarkus.mcp.server.support-langchain4j-annotations 自动注册。
 * 会话历史由 {@link AppAiChatMemory} 从 f_app_ai_conversation 装载。
 * 流式方法返回 {@link Multi}<{@link ChatEvent}>，支持流式下的工具执行与记忆。
 */
@RegisterAiService(tools = {AppTools.class, DirectoryTools.class})
public interface AssistantChat {

  /**
   * @param conversationKey appId:robotId:conversationId，用于装载会话历史
   * @param systemPrompt    动态系统提示（应用上下文 + 机器人角色设定）
   * @param message         用户消息
   */
  @SystemMessage("{{systemPrompt}}")
  Multi<ChatEvent> stream(@MemoryId String conversationKey, @V("systemPrompt") String systemPrompt,
                          @UserMessage String message);
}
