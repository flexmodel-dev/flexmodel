package dev.flexmodel.apps.ai;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.apps.ai.dto.AiDtos.Message;

import java.util.List;

/**
 * f_app_ai_conversation.messages JSON 列与 {@link Message} 列表的互转。
 * JSON 列在不同数据库驱动下可能返回 List，也可能返回原始 JSON 字符串（如 SQLite 测试库）。
 */
final class AiMessages {
  private AiMessages() {
  }

  static List<Message> parse(Object value) {
    Object parsed = value instanceof String text ? JsonUtils.parseToObject(text, List.class) : value;
    if (!(parsed instanceof List<?> values)) return List.of();
    return values.stream().map(item -> JsonUtils.convertValue(item, Message.class)).toList();
  }
}
