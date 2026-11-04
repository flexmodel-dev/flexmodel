package dev.flexmodel.apps.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class AiDtos {
  private AiDtos() {
  }

  public record RobotInput(
    @NotBlank @Size(max = 50) String name,
    @Size(max = 300) String description,
    @Size(max = 1000) String greeting,
    @Size(max = 6) List<@NotBlank @Size(max = 100) String> suggestions,
    @Size(max = 4000) String prompt,
    boolean enabled
  ) {
  }

  public record Robot(String id, String name, String description, String greeting,
                      List<String> suggestions, String prompt, boolean enabled) {
  }

  public record Message(String id, String role, String content, LocalDateTime createdAt) {
  }

  public record Conversation(String id, String robotId, String title, List<Message> messages,
                             LocalDateTime updatedAt) {
  }

  /**
   * 发送消息：appId 为隐藏的应用上下文（由前端按当前所在应用自动传递，不要求用户输入）；
   * 消息中以「@应用名」显式指定的应用优先于该隐藏值。
   */
  public record SendRequest(@NotBlank @Size(max = 4000) String content,
                            @Size(max = 200) String appId) {
  }

  /**
   * 创建会话：robotId 省略时使用保留的内置助手（assistant）。
   */
  public record ConversationRequest(@Size(max = 100) String robotId) {
  }

  /**
   * SSE 流式事件：type=token（content 为增量文本）、tool（content 为工具名）、
   * done（conversation 为完成后的完整会话）、error（content 为错误信息）。
   */
  public record StreamEvent(String type, String content, Conversation conversation) {
  }
}
