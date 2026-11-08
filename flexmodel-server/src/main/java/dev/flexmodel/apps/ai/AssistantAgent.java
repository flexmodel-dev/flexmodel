package dev.flexmodel.apps.ai;

import dev.flexmodel.apps.AppService;
import dev.flexmodel.apps.ai.dto.AiDtos.Robot;
import dev.flexmodel.apps.form.AppFormService;
import dev.flexmodel.apps.form.dto.AppFormFieldResponse;
import dev.flexmodel.apps.form.dto.AppFormResponse;
import dev.flexmodel.common.InternalServerException;
import io.quarkiverse.langchain4j.runtime.aiservice.ChatEvent;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.ConfigProvider;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 对话编排：系统提示组装 + 模型调用（quarkus-langchain4j 声明式 {@link AssistantChat}）。
 * 工具与 MCP 共用（{@link dev.flexmodel.mcp.AppTools} / {@link dev.flexmodel.mcp.DirectoryTools}），
 * appId 由系统提示告知模型、模型在工具参数中显式传递，写操作由 McpToolAccess 鉴权。
 * {@link AssistantChat} 经 Instance 懒加载，未配置模型时不会实例化模型 Bean。
 * 流式返回 {@link Multi}<{@link ChatEvent}>：token/工具事件实时下发，完成事件用于落库。
 */
@ApplicationScoped
@Slf4j
public class AssistantAgent {
  private static final int MAX_FORMS = 30;
  private static final int MAX_FIELDS = 40;

  @Inject
  AppService apps;
  @Inject
  AppFormService forms;
  @Any
  @Inject
  Instance<AssistantChat> assistant;

  /**
   * @param contextAppIds 本次消息的应用上下文（可多个，来自 @应用名 提及或前端隐藏参数）；
   *                      为空表示无应用上下文（跨应用模式）
   */
  public Multi<ChatEvent> stream(List<String> contextAppIds, String robotId, String conversationId, Robot robot, String content) {
    if (!modelConfigured()) {
      throw new InternalServerException("AI 模型未配置，请在系统设置中配置模型服务");
    }
    return assistant.get().stream(conversationKey(robotId, conversationId),
      systemPrompt(contextAppIds, robot), content);
  }

  private static String conversationKey(String robotId, String conversationId) {
    return robotId + ":" + conversationId;
  }

  private boolean modelConfigured() {
    // 扩展在未设置 api-key 时默认填充 "dummy"，同样视为未配置。
    String apiKey = ConfigProvider.getConfig()
      .getOptionalValue("quarkus.langchain4j.openai.api-key", String.class)
      .orElse("");
    return apiKey != null && !apiKey.isBlank() && !"dummy".equals(apiKey);
  }

  private String systemPrompt(List<String> appIds, Robot robot) {
    StringBuilder prompt = new StringBuilder();
    if (appIds.isEmpty()) {
      prompt.append("你是 Flexmodel 平台的 AI 助手，可以帮助用户创建应用、设计表单与工作台。\n")
        .append("当前没有绑定具体应用：用 list_apps 查看现有应用，或用 create_app 创建新应用。\n");
    } else if (appIds.size() == 1) {
      prompt.append("你是 Flexmodel 应用中的 AI 助手。\n")
        .append("当前应用ID：").append(appIds.getFirst())
        .append("，工具调用的 appId 参数必须使用该值。\n")
        .append("你可以查询应用数据，也可以设计表单、布局、视图、工作台、导航与审批流。\n");
    } else {
      prompt.append("你是 Flexmodel 平台的 AI 助手，本轮对话用户指定了多个应用上下文。\n")
        .append("可用应用ID：").append(String.join("、", appIds))
        .append("。工具调用的 appId 参数必须按问题涉及的应用从中选择。\n");
    }
    prompt.append("写操作仅限应用所有者或管理员，工具返回权限错误时请向用户说明。\n")
      .append("回答使用简体中文，必须基于工具返回的真实数据，不要编造记录、字段或统计结果；")
      .append("查询后说明数据范围，例如“共 N 条，展示前 M 条”。需要数据时先调用工具。\n")
      .append('\n').append(appCreationFlow());
    for (String appId : appIds) {
      prompt.append('\n').append(appContext(appId));
    }
    if (robot != null && robot.prompt() != null && !robot.prompt().isBlank()) {
      prompt.append("\n角色设定：\n").append(robot.prompt()).append('\n');
    }
    return prompt.toString();
  }

  /**
   * 应用创建流程 SOP：所有上下文分支共享，步骤编号与 AppTools 工具描述保持一致。
   * 目标是让模型按固定顺序一次性完整执行，而不是创建应用后就停下。
   */
  private String appCreationFlow() {
    return """
      【应用创建流程】当用户要求创建新应用、搭建或完善应用结构时，必须按以下主流程顺序调用工具完整执行，禁止跳过步骤：
      1. create_app：创建应用；appId 冲突或应用已存在时先用 list_apps / get_app 确认再继续。
      2. create_app_form：为每个业务对象创建表单与字段。
      3. save_app_form_layout：为每个新建表单保存布局。
      4. create_app_navigation：为工作台和各表单页面创建导航入口，否则页面无法访问。导航结构固定两级：一级必须先创建 GROUP 分组，二级 ENTRY 必须挂在 GROUP 下；禁止一级直接建 ENTRY、禁止 ENTRY 下再挂子菜单。
      5. create_app_mock_records：为每个业务表单创建 3~5 条示例记录，便于用户预览。
      增强步骤（在第 3 步与第 4 步之间按需执行）：
      - 视图：先 get_app_form_view_capabilities 获取视图契约，再用 save_app_form_views 保存完整视图列表（必须至少保留一个全部/表格视图）。
      - 工作台：先 list_app_workbenches 获取工作台，再 get_app_workbench_capabilities 获取组件契约，最后 save_app_workbench_layout 保存布局。
      执行要求：
      - 用户意图明确时必须一次性连续执行完所有步骤，不要执行一两步就停下来询问或等待确认；只有需求存在歧义时才先提问。
      - 某一步失败时根据工具返回的错误修正参数并重试；仍失败再向用户说明，然后继续后续步骤。
      - 用户只要求其中某一步时可单独执行该步，但完成后必须提示应用还缺哪些步骤。
      - 全部完成后输出创建结果摘要：应用、表单与布局、视图、工作台、导航、示例记录。
      """;
  }

  /**
   * 应用上下文：应用名与可查询业务表单及字段，减少首轮工具调用。
   */
  private String appContext(String appId) {
    String appName;
    try {
      appName = apps.findApp(appId).name();
    } catch (RuntimeException e) {
      appName = appId;
    }
    StringBuilder text = new StringBuilder();
    text.append("应用名称：").append(appName).append('\n');
    List<AppFormResponse> appForms = businessForms(appId);
    text.append("业务表单：\n");
    int formCount = 0;
    for (AppFormResponse form : appForms) {
      if (formCount++ >= MAX_FORMS) break;
      text.append("- ").append(form.name()).append('（').append(form.label()).append('）');
      if (form.description() != null && !form.description().isBlank()) {
        text.append("：").append(truncate(form.description(), 100));
      }
      text.append("\n  字段：");
      List<String> fields = new ArrayList<>();
      for (AppFormFieldResponse field : form.fields() == null ? List.<AppFormFieldResponse>of() : form.fields()) {
        if (fields.size() >= MAX_FIELDS) break;
        fields.add(field.name() + "(" + field.label() + ":" + field.type() + ")");
      }
      text.append(String.join("、", fields)).append('\n');
    }
    if (appForms.isEmpty()) text.append("（当前应用还没有业务表单）\n");
    return text.toString();
  }

  private List<AppFormResponse> businessForms(String appId) {
    return forms.findForms(appId).stream().filter(form -> !form.builtin()).toList();
  }

  private static String truncate(String value, int max) {
    return value.length() <= max ? value : value.substring(0, max) + "…";
  }
}
