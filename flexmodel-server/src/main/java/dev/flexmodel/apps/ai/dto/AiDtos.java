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

  /**
   * 消息内容块：reasoning 为供应商原生返回的推理文本，前端折叠展示；
   * 按出现顺序排列，一个块为一段连续的同类输出（工具调用后新一段推理是新块）。
   */
  public record MessagePart(String type, String content) {
  }

  public record Message(String id, String role, String content, LocalDateTime createdAt,
                        List<MessagePart> parts) {
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
   * SSE 流式事件：type=token（content 为增量文本）、reasoning（content 为推理增量文本）、
   * tool（content 为工具名）、change（成功写入的资源范围）、
   * done（conversation 为完成后的完整会话）、error（content 为错误信息）。
   * reasoning 块边界由事件类型切换隐式表达：token/tool 事件结束当前推理块，
   * 其后的 reasoning 增量属于新块。
   */
  public record StreamEvent(String type, String content, Conversation conversation, AppChange change) {
  }

  public record AppChange(String appId, String resource, String formName, String entityId) {
  }
}
