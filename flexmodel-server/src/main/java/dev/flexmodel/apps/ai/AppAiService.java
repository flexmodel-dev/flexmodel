package dev.flexmodel.apps.ai;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.apps.AppService;
import dev.flexmodel.apps.ai.dto.AiDtos.*;
import dev.flexmodel.apps.navigation.AppNavigationRepository;
import dev.flexmodel.apps.navigation.AppNavigationService;
import dev.flexmodel.apps.navigation.dto.AppNavRecord;
import dev.flexmodel.apps.workbench.AppWorkbenchRepository;
import dev.flexmodel.codegen.entity.AppAiConversation;
import dev.flexmodel.codegen.entity.AppAiRobot;
import dev.flexmodel.common.NotFoundException;
import dev.flexmodel.common.SessionContext;
import dev.flexmodel.common.ValidationException;
import io.quarkiverse.langchain4j.runtime.aiservice.ChatEvent;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 统一会话服务：会话仅按机器人 + 用户隔离，不按应用拆分。
 * 内置助手（assistant）不归属任何应用；当前应用上下文由前端在发送时以隐藏参数 appId 传入，
 * 消息中以「@应用名」显式指定的应用优先。应用机器人的上下文固定为其所属应用。
 */
@Slf4j
@ApplicationScoped
public class AppAiService {
  private static final int MAX_ROBOTS = 20;
  private static final String EMPTY_ANSWER = "抱歉，我没有找到相关信息，请换个问法或缩小查询范围。";
  /**
   * 顶部常驻助手的保留机器人ID：不落机器人表，任何登录用户都可直接对话。
   */
  public static final String ASSISTANT_ROBOT_ID = "assistant";
  /**
   * 助手会话落库时 app_id 列的虚拟值：不参与级联删除（应用 ID 不会与之冲突）。
   */
  public static final String ASSISTANT_APP_ID = "global";
  /**
   * 提及格式：@后跟非空白字符（支持中文应用名），取最后一个能解析到应用的提及。
   */
  private static final Pattern MENTION = Pattern.compile("@([^\\s@，。,\\.]{1,50})");

  @Inject
  AppAiRepository aiRepository;
  @Inject
  AppNavigationRepository appRepository;
  @Inject
  AppNavigationService navigationService;
  @Inject
  AppWorkbenchRepository workbenchRepository;
  @Inject
  AppService appService;
  @Inject
  SessionContext context;
  @Inject
  AssistantAgent agent;

  public List<Robot> robots(String appId) {
    requireUser();
    appRepository.requireAppProject(appId);
    return aiRepository.robots(appId).stream().map(this::toRobot)
      .filter(robot -> navigationService.isOwnerOrAdmin(appId) || robot.enabled() && canUse(appId, robot.id())).toList();
  }

  public Robot saveRobot(String appId, String id, RobotInput input) {
    requireManager(appId);
    if (input.name() == null || input.name().isBlank() || input.name().trim().length() > 50)
      throw new ValidationException("机器人名称不能为空且不能超过50个字符");
    if (input.suggestions() != null && (input.suggestions().size() > 6 || input.suggestions().stream()
      .anyMatch(value -> value == null || value.isBlank() || value.length() > 100)))
      throw new ValidationException("推荐问题最多6条，每条不超过100个字符");
    if (id == null && aiRepository.robots(appId).size() >= MAX_ROBOTS)
      throw new ValidationException("每个应用最多创建" + MAX_ROBOTS + "个机器人");
    AppAiRobot entity = id == null ? new AppAiRobot() : requireRobot(appId, id);
    if (id == null) {
      entity.setId(UUID.randomUUID().toString());
      entity.setAppId(appId);
      entity.setCreatedAt(LocalDateTime.now());
    }
    Robot robot = new Robot(entity.getId(), input.name().trim(), Objects.requireNonNullElse(input.description(), ""),
      Objects.requireNonNullElse(input.greeting(), "你好，有什么可以帮你？"),
      input.suggestions() == null ? List.of() : input.suggestions(), Objects.requireNonNullElse(input.prompt(), ""), input.enabled());
    entity.setConfig(JsonUtils.convertValue(robot, Map.class));
    entity.setUpdatedAt(LocalDateTime.now());
    aiRepository.save(entity);
    return robot;
  }

  public void deleteRobot(String appId, String id) {
    requireManager(appId);
    requireRobot(appId, id);
    var navs = appRepository.navRecords(appRepository.requireAppProject(appId));
    if (flatten(navs).stream().anyMatch(nav -> nav.destination() != null && id.equals(nav.destination().robotId())))
      throw new ValidationException("请先解除导航菜单对该机器人的绑定");
    if (workbenchRepository.findByAppId(appId).stream().anyMatch(workbench -> {
      if (workbench.getLayout() == null) return false;
      var layout = JsonUtils.convertValue(workbench.getLayout(), dev.flexmodel.apps.workbench.dto.WorkbenchLayoutRecord.class);
      return layout.components() != null && layout.components().stream().anyMatch(component -> component.config() instanceof
        dev.flexmodel.apps.workbench.dto.WorkbenchAiConfig ai && id.equals(ai.robotId()));
    })) throw new ValidationException("请先解除工作台组件对该机器人的绑定");
    aiRepository.deleteRobot(appId, id);
  }

  /**
   * 统一会话列表：robotId 省略时使用内置助手。
   */
  public List<Conversation> conversations(String robotId) {
    ResolvedRobot robot = usableRobot(robotId);
    return aiRepository.conversations(robot.robot().id(), requireUser()).stream()
      .limit(50).map(this::toConversation).toList();
  }

  public Conversation createConversation(String robotId) {
    ResolvedRobot robot = usableRobot(robotId);
    return createConversationEntity(robot);
  }

  private Conversation createConversationEntity(ResolvedRobot robot) {
    AppAiConversation entity = new AppAiConversation();
    entity.setId(UUID.randomUUID().toString());
    entity.setAppId(robot.appId() == null ? ASSISTANT_APP_ID : robot.appId());
    entity.setRobotId(robot.robot().id());
    entity.setUserId(requireUser());
    entity.setTitle("新对话");
    entity.setMessages(List.of());
    entity.setCreatedAt(LocalDateTime.now());
    entity.setUpdatedAt(LocalDateTime.now());
    aiRepository.save(entity);
    return toConversation(entity);
  }

  // 校验在流开始前同步执行；模型错误在流中转为 error 事件，仅成功完成后落库。
  public Multi<StreamEvent> streamSend(String id, SendRequest input) {
    requireUser();
    AppAiConversation entity = aiRepository.conversation(requireUser(), id);
    if (entity == null) throw new NotFoundException("会话不存在");
    ResolvedRobot robot = usableRobot(entity.getRobotId());
    List<String> contextApps = resolveContextApps(robot, input, entity.getRobotId());
    return doStream(entity, robot.robot(), contextApps, input);
  }

  private Multi<StreamEvent> doStream(AppAiConversation entity, Robot robot, List<String> contextApps, SendRequest input) {
    if (input.content() == null || input.content().isBlank() || input.content().length() > 4000)
      throw new ValidationException("消息不能为空且不能超过4000个字符");
    String robotId = entity.getRobotId();
    String id = entity.getId();
    List<Message> messages = new ArrayList<>(AiMessages.parse(entity.getMessages()));
    if (messages.size() >= 100) throw new ValidationException("当前会话已达100条消息，请开启新对话");
    String content = input.content().trim();
    if (messages.isEmpty()) entity.setTitle(content.substring(0, Math.min(content.length(), 40)));
    Message userMessage = new Message(UUID.randomUUID().toString(), "user", content, LocalDateTime.now(), null);
    AtomicBoolean completed = new AtomicBoolean();
    AtomicReference<Conversation> saved = new AtomicReference<>();
    // 请求级推理块累积：事件经 emitOn 后在同一线程串行处理，闭包内局部可变对象按请求隔离。
    List<MessagePart> reasoningParts = new ArrayList<>();
    StringBuilder currentReasoning = new StringBuilder();
    Runnable closeReasoning = () -> {
      if (!currentReasoning.isEmpty()) {
        reasoningParts.add(new MessagePart("reasoning", currentReasoning.toString()));
        currentReasoning.setLength(0);
      }
    };
    // 历史消息由 AppAiChatMemory 从会话实体装载，模型输出完成后统一持久化。
    // 事件回调发生在事件循环线程，切换到 worker 线程池执行映射与落库。
    return agent.stream(contextApps, robotId, id, robot, content)
      .emitOn(Infrastructure.getDefaultWorkerPool())
      .select().where(event -> event instanceof ChatEvent.PartialResponseEvent
        || event instanceof ChatEvent.PartialThinkingEvent
        || event instanceof ChatEvent.ToolExecutedEvent
        || event instanceof ChatEvent.ChatCompletedEvent)
      .onItem().transform(event -> {
        if (event instanceof ChatEvent.PartialThinkingEvent thinking) {
          currentReasoning.append(thinking.getText());
          return new StreamEvent("reasoning", thinking.getText(), null);
        }
        if (event instanceof ChatEvent.PartialResponseEvent partial) {
          closeReasoning.run();
          return new StreamEvent("token", partial.getChunk(), null);
        }
        if (event instanceof ChatEvent.ToolExecutedEvent tool) {
          closeReasoning.run();
          return new StreamEvent("tool", tool.getExecution().request().name(), null);
        }
        if (completed.compareAndSet(false, true)) {
          closeReasoning.run();
          var aiMessage = ((ChatEvent.ChatCompletedEvent) event).getChatResponse() == null
            ? null : ((ChatEvent.ChatCompletedEvent) event).getChatResponse().aiMessage();
          String answer = aiMessage == null || aiMessage.text() == null || aiMessage.text().isBlank()
            ? EMPTY_ANSWER : aiMessage.text();
          saved.set(completeReply(entity, messages, userMessage, answer, reasoningParts));
        }
        return new StreamEvent("done", null, saved.get());
      })
      .onFailure().recoverWithItem(error -> {
        log.warn("AI model call failed for conversation {}: {}", id, error.getMessage());
        return new StreamEvent("error", "AI 模型调用失败：" + error.getMessage(), null);
      });
  }

  /**
   * 解析本次消息的应用上下文（可多个）：@应用名 提及优先于隐藏参数 appId；
   * 应用机器人固定使用其所属应用；助手在两者都缺省时无应用上下文（跨应用模式）。
   */
  private List<String> resolveContextApps(ResolvedRobot robot, SendRequest input, String robotId) {
    if (!ASSISTANT_ROBOT_ID.equals(robotId)) return List.of(robot.appId());
    List<String> mentioned = resolveMentionedApps(input.content());
    if (!mentioned.isEmpty()) return mentioned;
    if (input.appId() != null && !input.appId().isBlank()) {
      String appId = input.appId().trim();
      appRepository.requireAppProject(appId);
      return List.of(appId);
    }
    return List.of();
  }

  /**
   * 按名称（精确、前缀）或 ID 匹配 @提及的应用；保留全部可解析的提及，按提及顺序去重。
   */
  private List<String> resolveMentionedApps(String content) {
    if (content == null || !content.contains("@")) return List.of();
    List<dev.flexmodel.apps.dto.AppResponse> apps;
    try {
      apps = appService.findApps();
    } catch (RuntimeException e) {
      return List.of();
    }
    LinkedHashSet<String> resolved = new LinkedHashSet<>();
    Matcher matcher = MENTION.matcher(content);
    while (matcher.find()) {
      String token = matcher.group(1).trim();
      if (token.isEmpty()) continue;
      String lower = token.toLowerCase();
      dev.flexmodel.apps.dto.AppResponse exact = apps.stream()
        .filter(app -> app.name() != null && app.name().equalsIgnoreCase(token)).findFirst().orElse(null);
      if (exact == null) exact = apps.stream()
        .filter(app -> app.name() != null && app.name().toLowerCase().startsWith(lower)).findFirst().orElse(null);
      if (exact == null) exact = apps.stream()
        .filter(app -> token.equals(app.id())).findFirst().orElse(null);
      if (exact != null) resolved.add(exact.id());
    }
    return List.copyOf(resolved);
  }

  // 同步落库在单实例内串行，防止同一会话并发发送时覆盖消息。
  private synchronized Conversation completeReply(AppAiConversation entity, List<Message> messages,
                                                  Message userMessage, String answer, List<MessagePart> parts) {
    messages.add(userMessage);
    // parts 只保存推理块；无推理输出时保持旧结构（parts 为 null）。
    messages.add(new Message(UUID.randomUUID().toString(), "assistant", answer, LocalDateTime.now(),
      parts.isEmpty() ? null : parts));
    entity.setMessages(JsonUtils.convertValue(messages, List.class));
    entity.setUpdatedAt(LocalDateTime.now());
    aiRepository.save(entity);
    return toConversation(entity);
  }

  public void deleteConversation(String id) {
    requireUser();
    AppAiConversation entity = aiRepository.conversation(requireUser(), id);
    if (entity == null) throw new NotFoundException("会话不存在");
    usableRobot(entity.getRobotId());
    aiRepository.deleteConversation(requireUser(), id);
  }

  public AppAiRobot requireRobot(String appId, String id) {
    var entity = aiRepository.robot(appId, id);
    if (entity == null) throw new NotFoundException("机器人不存在或不属于当前应用");
    return entity;
  }

  /**
   * 机器人解析结果：appId 为机器人所属应用，内置助手为 null。
   */
  private record ResolvedRobot(String appId, Robot robot) {
  }

  private ResolvedRobot usableRobot(String robotId) {
    requireUser();
    // 顶部常驻助手不依赖应用机器人配置：使用保留ID的个人会话，任何登录用户都可直接对话。
    if (robotId == null || robotId.isBlank() || ASSISTANT_ROBOT_ID.equals(robotId)) {
      return new ResolvedRobot(null, assistantRobot());
    }
    AppAiRobot entity = aiRepository.robot(robotId);
    if (entity == null) throw new NotFoundException("机器人不存在");
    String appId = entity.getAppId();
    appRepository.requireAppProject(appId);
    Robot robot = toRobot(entity);
    if (!robot.enabled() || !canUse(appId, robotId)) throw new ForbiddenException("当前机器人不可用");
    return new ResolvedRobot(appId, robot);
  }

  private Robot assistantRobot() {
    return new Robot(ASSISTANT_ROBOT_ID, "应用助手", "围绕当前应用回答问题",
      "你好，我可以帮你查询记录、整理信息，或分析业务情况。",
      List.of("最近有哪些新增记录？", "帮我汇总当前业务情况", "有哪些需要优先处理的事项？"),
      "围绕当前应用回答业务问题。", true);
  }

  private boolean canUse(String appId, String id) {
    if (navigationService.isOwnerOrAdmin(appId)) return true;
    var all = flatten(appRepository.navRecords(appRepository.requireAppProject(appId)));
    boolean bound = all.stream().anyMatch(nav -> nav.destination() != null && id.equals(nav.destination().robotId()));
    return !bound || visibleRobot(navigationService.findNavigation(appId), id);
  }

  private boolean visibleRobot(List<dev.flexmodel.apps.navigation.dto.AppNavResponse> navs, String id) {
    return navs.stream().anyMatch(nav -> nav.destination() != null && id.equals(nav.destination().robotId())
      || nav.children() != null && visibleRobot(nav.children(), id));
  }

  private List<AppNavRecord> flatten(List<AppNavRecord> navs) {
    List<AppNavRecord> result = new ArrayList<>();
    for (var nav : navs) {
      result.add(nav);
      if (nav.children() != null) result.addAll(flatten(nav.children()));
    }
    return result;
  }

  private Robot toRobot(AppAiRobot entity) {
    Robot robot = readJson(entity.getConfig(), Robot.class);
    if (robot == null) return null;
    return new Robot(robot.id(), robot.name(),
      Objects.requireNonNullElse(robot.description(), ""),
      Objects.requireNonNullElse(robot.greeting(), ""),
      robot.suggestions() == null ? List.of() : robot.suggestions(),
      Objects.requireNonNullElse(robot.prompt(), ""), robot.enabled());
  }

  private Conversation toConversation(AppAiConversation entity) {
    return new Conversation(entity.getId(), entity.getRobotId(), entity.getTitle(),
      AiMessages.parse(entity.getMessages()), entity.getUpdatedAt());
  }

  /**
   * JSON 列在不同数据库驱动下可能返回 Map/List，也可能返回原始 JSON 字符串；
   * SQLite 等测试库为后者，需要先解析再转换。
   */
  private <T> T readJson(Object value, Class<T> type) {
    if (value == null) return null;
    if (value instanceof String text) return JsonUtils.parseToObject(text, type);
    return JsonUtils.convertValue(value, type);
  }

  private String requireUser() {
    if (!context.isAuthenticatedUser() || context.getUserId() == null || context.getUserId().isBlank())
      throw new ForbiddenException("请先登录");
    return context.getUserId();
  }

  private void requireManager(String appId) {
    requireUser();
    if (!navigationService.isOwnerOrAdmin(appId)) throw new ForbiddenException("仅应用所有者或管理员可配置机器人");
  }
}
