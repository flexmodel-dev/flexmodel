package dev.flexmodel.apps;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.apps.dto.*;
import dev.flexmodel.common.NotFoundException;
import dev.flexmodel.common.SessionContext;
import dev.flexmodel.common.ValidationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * 记录历史查询：访问检查、分页与结果投影。变更详情按当前表单布局可见字段投影，
 * 无权访问的子表事件只保留通用提示。
 */
@ApplicationScoped
public class AppRecordHistoryService {

  private static final int DEFAULT_SIZE = 20;
  private static final int MAX_SIZE = 50;
  private static final int PREVIEW_LIMIT = 3;

  @Inject
  SessionContext sessionContext;
  @Inject
  AppFormService appFormService;
  @Inject
  AppNavigationService appNavigationService;
  @Inject
  AppRecordService appRecordService;
  @Inject
  AppRecordHistoryRepository repository;
  @Inject
  AppRecordHistoryRecorder recorder;

  public AppRecordHistoryPage page(String appId, String formName, String recordId, int size, String cursor) {
    AppFormResponse form = appFormService.findForm(appId, formName);
    if (form.builtin()) return AppRecordHistoryPage.unsupported();
    requireAccess(appId, formName, recordId);
    if (!recorder.captureSupported(appId)) return AppRecordHistoryPage.unsupported();
    int limit = Math.clamp(size <= 0 ? DEFAULT_SIZE : size, 1, MAX_SIZE);
    Cursor position = decodeCursor(cursor, formName, recordId);
    List<Map<String, Object>> targets = repository.findTargets(appId, formName, recordId,
      position == null ? null : position.occurredAt(), position == null ? null : position.eventId(), limit + 1);
    boolean hasMore = targets.size() > limit;
    if (hasMore) targets = targets.subList(0, limit);
    Map<String, Map<String, Object>> events = new LinkedHashMap<>();
    repository.findEvents(appId, targets.stream()
        .map(target -> String.valueOf(target.get("event_id")))
        .toList())
      .forEach(event -> events.put(String.valueOf(event.get("id")), event));
    List<AppRecordHistoryItem> list = new ArrayList<>();
    for (Map<String, Object> target : targets) {
      Map<String, Object> event = events.get(String.valueOf(target.get("event_id")));
      if (event == null) continue;
      list.add(item(appId, form, target, event));
    }
    String nextCursor = hasMore ? encodeCursor(targets.getLast(), formName, recordId) : null;
    return new AppRecordHistoryPage(true, list, nextCursor, hasMore);
  }

  public AppRecordHistoryDetail detail(String appId, String formName, String recordId, String eventId) {
    AppFormResponse form = appFormService.findForm(appId, formName);
    if (form.builtin()) throw new NotFoundException("历史不存在");
    requireAccess(appId, formName, recordId);
    if (!recorder.captureSupported(appId)) throw new NotFoundException("历史不存在");
    Map<String, Object> target = repository.findTarget(appId, eventId, formName, recordId);
    if (target == null) {
      throw new NotFoundException("历史不存在");
    }
    Map<String, Object> event = repository.findEvent(appId, eventId);
    if (event == null) throw new NotFoundException("历史不存在");
    String targetType = String.valueOf(target.get("target_type"));
    AppFormResponse eventForm = resolveEventForm(appId, form, event);
    boolean allowed = eventForm == null || eventForm.name().equals(form.name())
      || appNavigationService.canAccessForm(appId, eventForm.name());
    List<AppRecordHistoryChange> changes = allowed
      ? visibleChanges(eventForm, changesOf(event), Integer.MAX_VALUE).changes()
      : List.of();
    String recordLabel = allowed ? text(event, "record_label") : null;
    String summary = summary(appId, form, targetType, event, eventForm, allowed, changes.size());
    return new AppRecordHistoryDetail(
      String.valueOf(event.get("id")),
      text(event, "event_type"),
      text(event, "source"),
      targetType,
      text(event, "form_name").equals(form.name()) ? null : text(event, "form_name"),
      text(event, "actor_type"),
      text(event, "actor_id").isBlank() ? null : text(event, "actor_id"),
      text(event, "actor_name"),
      occurredAt(event),
      recordLabel,
      summary,
      changes,
      sanitizeContext(appId, event, allowed));
  }

  private void requireAccess(String appId, String formName, String recordId) {
    if (!sessionContext.isAuthenticatedUser()) throw new ForbiddenException("请登录后查看记录历史");
    String userId = sessionContext.getUserId();
    if (userId == null || userId.isBlank()) throw new ForbiddenException("请登录后查看记录历史");
    if (!appNavigationService.canAccessForm(appId, formName)) throw new ForbiddenException("无权访问该表单");
    appRecordService.requireRecordExists(appId, formName, recordId);
  }

  private AppRecordHistoryItem item(
    String appId,
    AppFormResponse timelineForm,
    Map<String, Object> target,
    Map<String, Object> event
  ) {
    String targetType = String.valueOf(target.get("target_type"));
    AppFormResponse eventForm = resolveEventForm(appId, timelineForm, event);
    boolean allowed = eventForm != null && (eventForm.name().equals(timelineForm.name())
      || appNavigationService.canAccessForm(appId, eventForm.name()));
    Projection projection = visibleChanges(eventForm, changesOf(event), PREVIEW_LIMIT);
    List<AppRecordHistoryChange> visible = allowed ? projection.changes() : List.of();
    String recordLabel = allowed ? text(event, "record_label") : null;
    int changeCount = allowed ? projection.totalVisible() : 0;
    return new AppRecordHistoryItem(
      String.valueOf(event.get("id")),
      text(event, "event_type"),
      text(event, "source"),
      targetType,
      text(event, "form_name").equals(timelineForm.name()) ? null : text(event, "form_name"),
      text(event, "actor_type"),
      text(event, "actor_id").isBlank() ? null : text(event, "actor_id"),
      text(event, "actor_name"),
      occurredAt(event),
      recordLabel,
      summary(appId, timelineForm, targetType, event, eventForm, allowed, changeCount),
      changeCount,
      visible,
      allowed && projection.totalVisible() > visible.size());
  }

  private String summary(
    String appId,
    AppFormResponse timelineForm,
    String targetType,
    Map<String, Object> event,
    AppFormResponse eventForm,
    boolean allowed,
    int visibleChangeCount
  ) {
    String eventType = text(event, "event_type");
    Map<String, Object> context = contextOf(event);
    if (AppRecordHistoryRecorder.EVENT_SUBMITTED.equals(eventType)) {
      return "提交了审批「" + text(context, "flowName") + "」";
    }
    if (AppRecordHistoryRecorder.EVENT_TASK_APPROVED.equals(eventType)) {
      return "同意了审批「" + text(context, "flowName") + "」";
    }
    if (AppRecordHistoryRecorder.EVENT_TASK_REJECTED.equals(eventType)) {
      return "拒绝了审批「" + text(context, "flowName") + "」";
    }
    if (AppRecordHistoryRecorder.EVENT_APPROVED.equals(eventType)) {
      return "审批「" + text(context, "flowName") + "」已通过";
    }
    if (AppRecordHistoryRecorder.EVENT_REJECTED.equals(eventType)) {
      return "审批「" + text(context, "flowName") + "」已拒绝";
    }
    if (AppRecordHistoryRecorder.EVENT_TERMINATED.equals(eventType)) {
      return "审批「" + text(context, "flowName") + "」已终止";
    }
    String label = allowed && text(event, "record_label") != null && !text(event, "record_label").isBlank()
      ? "「" + text(event, "record_label") + "」"
      : "";
    if ("PARENT".equals(targetType)) {
      return switch (eventType) {
        case "RECORD_CREATED" -> "新增了子记录" + label;
        case "RECORD_DELETED" -> "删除了子记录" + label;
        default -> subformMoveSummary(timelineForm, context, label);
      };
    }
    if ("RECORD_CREATED".equals(eventType)) return "创建了记录";
    if ("RECORD_DELETED".equals(eventType)) return "删除了记录";
    return visibleChangeCount > 0 ? "修改了 " + visibleChangeCount + " 个字段" : "修改了记录";
  }

  /**
   * 主子表关联变化：移出旧主记录、移入新主记录；其余展示为修改子记录。
   */
  private String subformMoveSummary(AppFormResponse timelineForm, Map<String, Object> context, String label) {
    for (Map<String, Object> entry : parentsOf(context)) {
      if (!timelineForm.name().equals(text(entry, "form"))) continue;
      String before = text(entry, "before");
      String after = text(entry, "after");
      if (before.isBlank() && !after.isBlank()) return "移入了子记录" + label;
      if (!before.isBlank() && after.isBlank()) return "移出了子记录" + label;
      if (!before.isBlank()) return "移入了子记录" + label;
    }
    return "修改了子记录" + label;
  }

  private record Projection(List<AppRecordHistoryChange> changes, int totalVisible) {
  }

  /**
   * 按当前表单布局可见字段投影事件保存的字段变化。
   */
  private Projection visibleChanges(AppFormResponse form, List<Map<String, Object>> changes, int limit) {
    if (form == null || changes.isEmpty()) return new Projection(List.of(), 0);
    LinkedHashSet<String> exposed = new LinkedHashSet<>(appRecordService.exposedFields(form));
    List<AppRecordHistoryChange> result = new ArrayList<>();
    for (Map<String, Object> change : changes) {
      if (!exposed.contains(String.valueOf(change.get("fieldName")))) continue;
      result.add(new AppRecordHistoryChange(
        String.valueOf(change.get("fieldName")),
        text(change, "fieldLabel"),
        text(change, "fieldType"),
        change.get("before"),
        change.get("after"),
        text(change, "beforeDisplay").isBlank() ? null : text(change, "beforeDisplay"),
        text(change, "afterDisplay").isBlank() ? null : text(change, "afterDisplay")));
      if (result.size() >= limit) break;
    }
    int total = (int) changes.stream()
      .map(change -> String.valueOf(change.get("fieldName")))
      .filter(exposed::contains)
      .count();
    return new Projection(result, total);
  }

  private AppFormResponse resolveEventForm(String appId, AppFormResponse timelineForm, Map<String, Object> event) {
    String eventFormName = text(event, "form_name");
    if (eventFormName.equals(timelineForm.name())) return timelineForm;
    try {
      return appFormService.findForm(appId, eventFormName);
    } catch (RuntimeException error) {
      return null;
    }
  }

  private List<Map<String, Object>> changesOf(Map<String, Object> event) {
    Object value = event.get("changes");
    if (value instanceof List<?> list) {
      return list.stream()
        .filter(item -> item instanceof Map<?, ?>)
        .map(item -> {
          Map<String, Object> change = new LinkedHashMap<>();
          ((Map<?, ?>) item).forEach((key, entryValue) -> change.put(String.valueOf(key), entryValue));
          return change;
        })
        .toList();
    }
    if (value instanceof String text && !text.isBlank()) {
      try {
        return JsonUtils.parseToMapList(text);
      } catch (RuntimeException ignored) {
        return List.of();
      }
    }
    return List.of();
  }

  private Map<String, Object> contextOf(Map<String, Object> event) {
    Object value = event.get("context");
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> context = new LinkedHashMap<>();
      map.forEach((key, entryValue) -> context.put(String.valueOf(key), entryValue));
      return context;
    }
    if (value instanceof String text && !text.isBlank()) {
      try {
        return JsonUtils.parseToMap(text);
      } catch (RuntimeException ignored) {
        return Map.of();
      }
    }
    return Map.of();
  }

  private List<Map<String, Object>> parentsOf(Map<String, Object> context) {
    Object value = context.get("parents");
    if (value instanceof List<?> list) {
      return list.stream()
        .filter(item -> item instanceof Map<?, ?>)
        .map(item -> {
          Map<String, Object> entry = new LinkedHashMap<>();
          ((Map<?, ?>) item).forEach((key, entryValue) -> entry.put(String.valueOf(key), entryValue));
          return entry;
        })
        .toList();
    }
    return List.of();
  }

  /**
   * 详情上下文白名单投影；审批意见仅对签核人、发起人和管理员可见。
   */
  private Map<String, Object> sanitizeContext(String appId, Map<String, Object> event, boolean allowed) {
    Map<String, Object> source = contextOf(event);
    Map<String, Object> result = new LinkedHashMap<>();
    for (String key : List.of("flowName", "nodeTitle", "importId", "approvalInstanceId", "nodeId")) {
      if (source.containsKey(key)) result.put(key, source.get(key));
    }
    String userId = sessionContext.getUserId();
    String actorId = text(event, "actor_id");
    String initiator = text(source, "initiator");
    boolean commentVisible = allowed && userId != null && (userId.equals(actorId) || userId.equals(initiator)
      || appNavigationService.isOwnerOrAdmin(appId));
    if (commentVisible && source.containsKey("comment")) result.put("comment", source.get("comment"));
    return result;
  }

  private String occurredAt(Map<String, Object> event) {
    Object value = event.get("occurred_at");
    if (value instanceof LocalDateTime time) {
      return time.atOffset(ZoneOffset.UTC).toString();
    }
    return value == null ? null : String.valueOf(value);
  }

  private record Cursor(LocalDateTime occurredAt, String eventId) {
  }

  private Cursor decodeCursor(String cursor, String formName, String recordId) {
    if (cursor == null || cursor.isBlank()) return null;
    try {
      String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
      Map<String, Object> raw = JsonUtils.parseToMap(decoded);
      if (!Objects.equals(formName, String.valueOf(raw.get("f")))
        || !Objects.equals(recordId, String.valueOf(raw.get("r")))) {
        throw new ValidationException("分页游标不合法");
      }
      LocalDateTime occurredAt = LocalDateTime.parse(String.valueOf(raw.get("o")));
      String eventId = String.valueOf(raw.get("e"));
      if (eventId.isBlank()) throw new ValidationException("分页游标不合法");
      return new Cursor(occurredAt, eventId);
    } catch (IllegalArgumentException | DateTimeParseException error) {
      throw new ValidationException("分页游标不合法");
    }
  }

  private String encodeCursor(Map<String, Object> target, String formName, String recordId) {
    Object occurredAt = target.get("occurred_at");
    String occurredText = occurredAt instanceof LocalDateTime time ? time.toString() : String.valueOf(occurredAt);
    Map<String, Object> raw = Map.of(
      "o", occurredText,
      "e", String.valueOf(target.get("event_id")),
      "f", formName,
      "r", recordId);
    return Base64.getUrlEncoder().withoutPadding()
      .encodeToString(JsonUtils.toJsonString(raw).getBytes(StandardCharsets.UTF_8));
  }

  private static String text(Map<String, Object> row, String key) {
    Object value = row.get(key);
    return value == null ? "" : String.valueOf(value);
  }
}
