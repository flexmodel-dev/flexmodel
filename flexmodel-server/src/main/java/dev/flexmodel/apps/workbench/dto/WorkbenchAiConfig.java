package dev.flexmodel.apps.workbench.dto;

/**
 * 快捷提问或工作台内嵌对话，共用应用机器人与个人会话。
 */
public record WorkbenchAiConfig(String robotId, String displayMode, Boolean showSuggestions)
  implements WorkbenchComponentConfig {
}
