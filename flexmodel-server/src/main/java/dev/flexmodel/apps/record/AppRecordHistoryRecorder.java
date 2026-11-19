package dev.flexmodel.apps.record;

import dev.flexmodel.apps.form.AppFormService;

import dev.flexmodel.apps.form.dto.AppFormFieldResponse;
import dev.flexmodel.apps.form.dto.AppFormResponse;
import dev.flexmodel.common.ProjectUnitOfWork;
import dev.flexmodel.common.SessionContext;
import dev.flexmodel.common.SqlErrors;
import dev.flexmodel.directory.DirectoryService;
import dev.flexmodel.project.ProjectService;
import dev.flexmodel.session.SessionFactory;
import dev.flexmodel.sql.JdbcSchemaProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static dev.flexmodel.common.Values.objects;
import static dev.flexmodel.common.Values.text;

/**
 * 记录历史记录器：接收操作时复制完成的事件数据，业务事务提交后安全写入平台库。
 * <p>
 * 历史失败只记录错误日志，不影响已成功的业务结果；唯一键重复视为已记录。
 */
@ApplicationScoped
@Slf4j
public class AppRecordHistoryRecorder {

  public static final String SOURCE_APPS = "APPS";
  public static final String SOURCE_IMPORT = "IMPORT";
  public static final String SOURCE_APPROVAL = "APPROVAL";
  public static final String ACTOR_USER = "USER";
  public static final String ACTOR_SYSTEM = "SYSTEM";
  public static final String TARGET_SELF = "SELF";
  public static final String TARGET_PARENT = "PARENT";

  static final String EVENT_CREATED = "RECORD_CREATED";
  static final String EVENT_UPDATED = "RECORD_UPDATED";
  static final String EVENT_DELETED = "RECORD_DELETED";
  static final String EVENT_SUBMITTED = "FLOW_STARTED";
  static final String EVENT_TASK_APPROVED = "APPROVAL_TASK_APPROVED";
  static final String EVENT_TASK_REJECTED = "APPROVAL_TASK_REJECTED";
  static final String EVENT_APPROVED = "FLOW_SUCCEEDED";
  static final String EVENT_REJECTED = "FLOW_REJECTED";
  static final String EVENT_TERMINATED = "FLOW_TERMINATED";

  private static final Set<String> REFERENCE_SYSTEM_FIELDS = Set.of("created_by", "updated_by", "owner_id");
  private static final int RECORD_LABEL_MAX = 255;
  private static final int PERSIST_BATCH = 200;

  @Inject
  AppRecordHistoryRepository repository;
  @Inject
  AppRecordHistoryContext historyContext;
  @Inject
  AppFormService appFormService;
  @Inject
  ProjectService projectService;
  @Inject
  SessionFactory sessionFactory;
  @Inject
  SessionContext sessionContext;
  @Inject
  DirectoryService directoryService;
  @Inject
  ProjectUnitOfWork transactions;

  /**
   * 历史采集仅支持 SQL 数据源：非 SQL 数据源保留原 CRUD，不做强制事务。
   */
  public boolean captureSupported(String appId) {
    try {
      String databaseName = projectService.resolveDatabaseName(appId);
      return sessionFactory.getSchemaProvider(databaseName) instanceof JdbcSchemaProvider;
    } catch (RuntimeException error) {
      log.debug("History capture unavailable for app {}: {}", appId, error.getMessage());
      return false;
    }
  }

  public void created(String appId, AppFormResponse form, Map<String, Object> values) {
    try {
      if (form.builtin() || values == null) return;
      String recordId = String.valueOf(values.get("id"));
      List<ParentLink> links = parentLinks(appId, form);
      List<Map<String, Object>> parents = parentRelations(form, values, links);
      record(baseEvent(appId, form, recordId, values, EVENT_CREATED)
        .changes(AppRecordHistoryDiff.initialValues(form, values))
        .context(mergeContexts(approvalContext(), parentContext(List.of(), parents)))
        .targets(parentTargets(form.name(), recordId, List.of(), parents))
        .build());
    } catch (RuntimeException error) {
      log.warn("Failed to build record created history for {}/{}: {}", appId, form.name(), error.getMessage());
    }
  }

  public void updated(
    String appId,
    AppFormResponse form,
    String recordId,
    Map<String, Object> before,
    Map<String, Object> after
  ) {
    try {
      if (form.builtin()) return;
      List<Map<String, Object>> changes = AppRecordHistoryDiff.changes(form, before, after);
      if (changes.isEmpty()) return;
      List<ParentLink> links = parentLinks(appId, form);
      List<Map<String, Object>> parentsBefore = parentRelations(form, before, links);
      List<Map<String, Object>> parentsAfter = parentRelations(form, after, links);
      record(baseEvent(appId, form, recordId, after, EVENT_UPDATED)
        .changes(changes)
        .context(mergeContexts(approvalContext(), parentContext(parentsBefore, parentsAfter)))
        .targets(parentTargets(form.name(), recordId, parentsBefore, parentsAfter))
        .build());
    } catch (RuntimeException error) {
      log.warn("Failed to build record updated history for {}/{}: {}", appId, form.name(), error.getMessage());
    }
  }

  public void deleted(String appId, AppFormResponse form, String recordId, Map<String, Object> before) {
    try {
      if (form.builtin()) return;
      List<Map<String, Object>> parents = parentRelations(form, before, parentLinks(appId, form));
      record(baseEvent(appId, form, recordId, before, EVENT_DELETED)
        .context(mergeContexts(approvalContext(), parentContext(parents, List.of())))
        .targets(parentTargets(form.name(), recordId, parents, List.of()))
        .build());
    } catch (RuntimeException error) {
      log.warn("Failed to build record deleted history for {}/{}: {}", appId, form.name(), error.getMessage());
    }
  }

  /**
   * 已声明的主子关系：父表单布局中存在指向本表单的 SUBFORM 项。
   */
  record ParentLink(String fieldName, String formName, String label) {
  }

  /**
   * 批量导入共享上下文：目录显示名、表单快照与主子关系只在导入开始时解析一次。
   * <p>
   * 逐行调用 {@code findForm} / {@code directory} 会让一次 5000 行的导入产生数万次数据库往返，
   * 因此导入路径必须复用本上下文，不得在行循环内查询元数据。
   */
  public static final class ImportContext {
    private final String appId;
    private final String userId;
    private final String actorName;
    private final Map<String, List<ParentLink>> parents;

    private ImportContext(String appId, String userId, String actorName, Map<String, List<ParentLink>> parents) {
      this.appId = appId;
      this.userId = userId;
      this.actorName = actorName;
      this.parents = parents;
    }

    public String appId() {
      return appId;
    }

    public String userId() {
      return userId;
    }

    String actorName() {
      return actorName;
    }

    List<ParentLink> parents(String formName) {
      return parents.getOrDefault(formName, List.of());
    }
  }

  /**
   * 导入前一次性解析：成员显示名查一次目录，主子关系按表单快照建索引。
   */
  public ImportContext prepareImportContext(String appId, String userId, Collection<AppFormResponse> forms) {
    Map<String, AppFormResponse> byName = new LinkedHashMap<>();
    for (AppFormResponse form : forms == null ? List.<AppFormResponse>of() : forms) {
      if (form != null && !form.builtin()) byName.put(form.name(), form);
    }
    return new ImportContext(appId, userId, memberName(userId), indexParentLinks(byName));
  }

  private Map<String, List<ParentLink>> indexParentLinks(Map<String, AppFormResponse> forms) {
    Map<String, List<ParentLink>> result = new LinkedHashMap<>();
    for (AppFormResponse child : forms.values()) {
      List<ParentLink> links = new ArrayList<>();
      for (AppFormFieldResponse field : child.fields()) {
        if (!isBusinessReference(field)) continue;
        AppFormResponse parent = forms.get(field.referenceForm());
        if (parent == null || !declaresSubform(parent, child, field)) continue;
        links.add(new ParentLink(field.name(), parent.name(),
          parent.label() == null ? parent.name() : parent.label()));
      }
      if (!links.isEmpty()) result.put(child.name(), List.copyOf(links));
    }
    return result;
  }

  private boolean isBusinessReference(AppFormFieldResponse field) {
    return "REFERENCE".equals(field.type()) && !field.builtin()
      && !REFERENCE_SYSTEM_FIELDS.contains(field.name());
  }

  private boolean declaresSubform(AppFormResponse parent, AppFormResponse child, AppFormFieldResponse field) {
    if (parent.layout() == null || parent.layout().items() == null) return false;
    return parent.layout().items().stream().anyMatch(item -> item != null
      && "SUBFORM".equals(item.type())
      && child.name().equals(item.formName())
      && field.name().equals(item.referenceField()));
  }

  /**
   * 整包导入：同一次导入共用 operationId 与稳定去重键，元数据取自导入上下文。
   */
  public AppRecordHistoryEvent importCreated(
    ImportContext context,
    String importId,
    AppFormResponse form,
    Map<String, Object> values
  ) {
    if (form.builtin() || values == null) return null;
    try {
      String recordId = String.valueOf(values.get("id"));
      List<Map<String, Object>> parents = parentRelations(form, values, context.parents(form.name()));
      return new EventBuilder()
        .appId(context.appId())
        .formName(form.name())
        .formLabel(form.label())
        .recordId(recordId)
        .recordLabel(recordLabel(form, values))
        .eventType(EVENT_CREATED)
        .occurredAt(LocalDateTime.now(ZoneOffset.UTC))
        .source(SOURCE_IMPORT)
        .operationId(importId)
        .actor(ACTOR_USER, context.userId(), context.actorName())
        .eventKey(digest(importId, form.name(), recordId, EVENT_CREATED))
        .changes(AppRecordHistoryDiff.initialValues(form, values))
        .context(mergeContexts(approvalContext(), parentContext(List.of(), parents)))
        .targets(parentTargets(form.name(), recordId, List.of(), parents))
        .build();
    } catch (RuntimeException error) {
      log.warn("Failed to build import history for {}/{}/{}: {}", context.appId(), form.name(), importId,
        error.getMessage());
      return null;
    }
  }

  public void recordBatch(List<AppRecordHistoryEvent> events) {
    List<AppRecordHistoryEvent> copied = events.stream().filter(Objects::nonNull).toList();
    if (copied.isEmpty()) return;
    ProjectUnitOfWork.afterCommit(() -> {
      for (int index = 0; index < copied.size(); index += PERSIST_BATCH) {
        persistBatch(copied.subList(index, Math.min(index + PERSIST_BATCH, copied.size())));
      }
    });
  }

  public void flowStarted(
    String appId,
    String formName,
    String recordId,
    String flowName,
    String actorId,
    String instanceId
  ) {
    try {
      record(baseEvent(appId, resolveForm(appId, formName), recordId, null, EVENT_SUBMITTED)
        .source(SOURCE_APPROVAL)
        .actor(ACTOR_USER, actorId, memberName(actorId))
        .operationId(instanceId)
        .eventKey(digest(instanceId, EVENT_SUBMITTED))
        .context(contextOf("flowName", flowName, "approvalInstanceId", instanceId))
        .build());
    } catch (RuntimeException error) {
      log.warn("Failed to build approval submitted history for {}/{}/{}: {}", appId, formName, recordId, error.getMessage());
    }
  }

  public void taskDecided(
    String appId,
    String formName,
    String recordId,
    String decision,
    String comment,
    String actorId,
    String nodeTitle,
    String flowName,
    String instanceId,
    String taskId
  ) {
    try {
      boolean approved = "approve".equals(decision);
      Map<String, Object> context = contextOf(
        "flowName", flowName,
        "nodeTitle", nodeTitle,
        "comment", comment,
        "approvalInstanceId", instanceId,
        "taskId", taskId);
      record(baseEvent(appId, resolveForm(appId, formName), recordId, null,
        approved ? EVENT_TASK_APPROVED : EVENT_TASK_REJECTED)
        .source(SOURCE_APPROVAL)
        .actor(ACTOR_USER, actorId, memberName(actorId))
        .operationId(instanceId)
        .eventKey(digest(taskId, decision))
        .context(context)
        .build());
    } catch (RuntimeException error) {
      log.warn("Failed to build approval task history for {}/{}: {}", appId, taskId, error.getMessage());
    }
  }

  public void flowFinished(
    String appId,
    String formName,
    String recordId,
    String status,
    String flowName,
    String initiator,
    String instanceId
  ) {
    try {
      String eventType = switch (status) {
        case "SUCCEEDED" -> EVENT_APPROVED;
        case "REJECTED" -> EVENT_REJECTED;
        default -> EVENT_TERMINATED;
      };
      Map<String, Object> context = contextOf(
        "flowName", flowName,
        "approvalInstanceId", instanceId,
        "initiator", initiator);
      record(baseEvent(appId, resolveForm(appId, formName), recordId, null, eventType)
        .source(SOURCE_APPROVAL)
        .actor(ACTOR_SYSTEM, null, "系统")
        .operationId(instanceId)
        .eventKey(digest(instanceId, status))
        .context(context)
        .build());
    } catch (RuntimeException error) {
      log.warn("Failed to build approval finished history for {}/{}: {}", appId, instanceId, error.getMessage());
    }
  }

  public void record(AppRecordHistoryEvent event) {
    if (event == null) return;
    ProjectUnitOfWork.afterCommit(() -> persist(event));
  }

  private void persist(AppRecordHistoryEvent event) {
    try {
      transactions.executePlatform(event.appId(), () -> {
        String eventId = UUID.randomUUID().toString();
        repository.insertEvent(event.appId(), eventRow(event, eventId));
        repository.insertTargets(event.appId(), targetRows(event, eventId));
        return null;
      });
    } catch (RuntimeException error) {
      if (SqlErrors.constraintViolation(error)) {
        log.debug("App record history already recorded: appId={} eventKey={}", event.appId(), event.eventKey());
        return;
      }
      log.error("Failed to record app record history: appId={} form={} record={} eventType={} operationId={} error={}",
        event.appId(), event.formName(), event.recordId(), event.eventType(), event.operationId(), error.getMessage());
    }
  }

  /**
   * 批量写历史：一批只开一个平台事务、两条批量插入，避免逐事件开关会话。
   */
  private void persistBatch(List<AppRecordHistoryEvent> events) {
    try {
      String appId = events.getFirst().appId();
      transactions.executePlatform(appId, () -> {
        List<Map<String, Object>> eventRows = new ArrayList<>(events.size());
        List<Map<String, Object>> pendingTargets = new ArrayList<>(events.size() * 2);
        for (AppRecordHistoryEvent event : events) {
          String eventId = UUID.randomUUID().toString();
          eventRows.add(eventRow(event, eventId));
          pendingTargets.addAll(targetRows(event, eventId));
        }
        repository.insertEvents(appId, eventRows);
        repository.insertTargets(appId, pendingTargets);
        return null;
      });
    } catch (RuntimeException error) {
      if (SqlErrors.constraintViolation(error)) {
        // 批次中存在重复事件时逐条降级，保留其余可记录事件
        events.forEach(this::persist);
        return;
      }
      log.error("Failed to record app record history batch ({} events) for app {}: {}",
        events.size(), events.getFirst().appId(), error.getMessage());
    }
  }

  private Map<String, Object> eventRow(AppRecordHistoryEvent event, String eventId) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", eventId);
    row.put("event_key", event.eventKey());
    if (event.operationId() != null) row.put("operation_id", event.operationId());
    row.put("form_name", event.formName());
    if (event.formLabel() != null) row.put("form_label", event.formLabel());
    row.put("record_id", event.recordId());
    if (event.recordLabel() != null) row.put("record_label", event.recordLabel());
    row.put("event_type", event.eventType());
    row.put("source", event.source());
    row.put("actor_type", event.actorType());
    if (event.actorId() != null) row.put("actor_id", event.actorId());
    row.put("actor_name", event.actorName());
    row.put("occurred_at", event.occurredAt());
    row.put("recorded_at", LocalDateTime.now(ZoneOffset.UTC));
    if (event.changes() != null && !event.changes().isEmpty()) row.put("changes", event.changes());
    if (event.context() != null && !event.context().isEmpty()) row.put("context", event.context());
    return row;
  }

  private List<Map<String, Object>> targetRows(AppRecordHistoryEvent event, String eventId) {
    List<Map<String, Object>> rows = new ArrayList<>();
    LinkedHashSet<String> seen = new LinkedHashSet<>();
    for (AppRecordHistoryEvent.Target target : event.targets()) {
      if (!seen.add(target.formName() + "\u0000" + target.recordId())) continue;
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("id", UUID.randomUUID().toString());
      row.put("event_id", eventId);
      row.put("form_name", target.formName());
      row.put("record_id", target.recordId());
      row.put("target_type", target.targetType());
      row.put("occurred_at", event.occurredAt());
      rows.add(row);
    }
    return rows;
  }

  private EventBuilder baseEvent(String appId, AppFormResponse form, String recordId,
                                 Map<String, Object> record, String eventType) {
    EventBuilder builder = new EventBuilder()
      .appId(appId)
      .formName(form.name())
      .formLabel(form.label())
      .recordId(recordId)
      .recordLabel(recordLabel(form, record))
      .eventType(eventType)
      .occurredAt(LocalDateTime.now(ZoneOffset.UTC))
      .eventKey(UUID.randomUUID().toString());
    AppRecordHistoryContext.Snapshot approval = historyContext.current();
    if (approval != null) {
      builder.source(SOURCE_APPROVAL)
        .actor(ACTOR_SYSTEM, null, "系统")
        .operationId(approval.approvalInstanceId());
    } else {
      String userId = currentUserId();
      builder.source(SOURCE_APPS)
        .actor(ACTOR_USER, userId, memberName(userId));
    }
    builder.targets(List.of(new AppRecordHistoryEvent.Target(form.name(), recordId, TARGET_SELF)));
    return builder;
  }

  private Map<String, Object> approvalContext() {
    AppRecordHistoryContext.Snapshot approval = historyContext.current();
    if (approval == null) return null;
    return contextOf(
      "approvalInstanceId", approval.approvalInstanceId(),
      "nodeId", approval.nodeId(),
      "initiator", approval.initiator());
  }

  private Map<String, Object> mergeContexts(Map<String, Object> base, Map<String, Object> override) {
    if (base == null || base.isEmpty()) return override;
    if (override == null || override.isEmpty()) return base;
    Map<String, Object> merged = new LinkedHashMap<>(base);
    merged.putAll(override);
    return merged;
  }

  private String currentUserId() {
    String userId = sessionContext.getUserId();
    return userId == null || userId.isBlank() ? "admin" : userId;
  }

  private String memberName(String userId) {
    if (userId == null || userId.isBlank()) return "系统";
    try {
      for (Map<String, Object> member : objects(directoryService.directory().get("members"))) {
        if (userId.equals(text(member, "id"))) return text(member, "name");
      }
    } catch (RuntimeException ignored) {
      // 目录不可用时回退显示用户 ID
    }
    return userId;
  }

  private AppFormResponse resolveForm(String appId, String formName) {
    AppFormResponse form = appFormService.findForm(appId, formName);
    if (form.builtin()) throw new IllegalArgumentException("内置表单不支持历史");
    return form;
  }

  private String recordLabel(AppFormResponse form, Map<String, Object> record) {
    if (record == null) return null;
    String displayField = form.displayField();
    Object value = displayField == null || displayField.isBlank() ? null : record.get(displayField);
    if (value == null || "".equals(value) || (value instanceof String text && text.isBlank())) {
      return null;
    }
    String label = String.valueOf(value);
    return label.length() <= RECORD_LABEL_MAX ? label : label.substring(0, RECORD_LABEL_MAX);
  }

  /**
   * 只汇总表单布局中明确声明的主子表关系：子表单引用字段指向的主表单在布局中声明了 SUBFORM 项。
   */
  private List<Map<String, Object>> parentRelations(AppFormResponse form, Map<String, Object> record,
                                                    List<ParentLink> links) {
    List<Map<String, Object>> result = new ArrayList<>();
    if (record == null || links.isEmpty()) return result;
    for (ParentLink link : links) {
      String parentRecordId = referenceRecordId(record.get(link.fieldName()));
      if (parentRecordId == null || parentRecordId.isBlank()) continue;
      Map<String, Object> relation = new LinkedHashMap<>();
      relation.put("form", link.formName());
      relation.put("label", link.label());
      relation.put("field", link.fieldName());
      relation.put("recordId", parentRecordId);
      result.add(relation);
    }
    return result;
  }

  /**
   * 单条 CRUD 路径：按需解析主子关系（每次操作至多一次 findForm，不进入批量导入循环）。
   */
  private List<ParentLink> parentLinks(String appId, AppFormResponse form) {
    List<ParentLink> links = new ArrayList<>();
    for (AppFormFieldResponse field : form.fields()) {
      if (!isBusinessReference(field)) continue;
      String parentFormName = field.referenceForm();
      if (parentFormName == null || parentFormName.isBlank()) continue;
      AppFormResponse parentForm;
      try {
        parentForm = appFormService.findForm(appId, parentFormName);
      } catch (RuntimeException error) {
        continue;
      }
      if (parentForm.builtin() || !declaresSubform(parentForm, form, field)) continue;
      links.add(new ParentLink(field.name(), parentForm.name(),
        parentForm.label() == null ? parentForm.name() : parentForm.label()));
    }
    return links;
  }

  private String referenceRecordId(Object value) {
    if (value == null) return null;
    if (value instanceof List<?> values && !values.isEmpty()) {
      return referenceRecordId(values.getFirst());
    }
    if (value instanceof Map<?, ?> record) {
      Object id = record.get("id");
      return id == null ? null : String.valueOf(id);
    }
    String id = String.valueOf(value);
    return id.isBlank() ? null : id;
  }

  private Map<String, Object> parentContext(
    List<Map<String, Object>> parentsBefore,
    List<Map<String, Object>> parentsAfter
  ) {
    Map<String, String> beforeByForm = new LinkedHashMap<>();
    for (Map<String, Object> relation : parentsBefore) {
      beforeByForm.put(text(relation, "form"), text(relation, "recordId"));
    }
    Map<String, String> afterByForm = new LinkedHashMap<>();
    for (Map<String, Object> relation : parentsAfter) {
      afterByForm.put(text(relation, "form"), text(relation, "recordId"));
    }
    List<Map<String, Object>> entries = new ArrayList<>();
    LinkedHashSet<String> forms = new LinkedHashSet<>();
    parentsBefore.forEach(relation -> forms.add(text(relation, "form")));
    parentsAfter.forEach(relation -> forms.add(text(relation, "form")));
    for (String formName : forms) {
      String beforeId = beforeByForm.get(formName);
      String afterId = afterByForm.get(formName);
      if (Objects.equals(beforeId, afterId)) {
        continue;
      }
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("form", formName);
      entry.put("before", beforeId);
      entry.put("after", afterId);
      entries.add(entry);
    }
    if (entries.isEmpty()) return null;
    return contextOf("parents", entries);
  }

  private List<AppRecordHistoryEvent.Target> parentTargets(
    String formName,
    String recordId,
    List<Map<String, Object>> parentsBefore,
    List<Map<String, Object>> parentsAfter
  ) {
    List<AppRecordHistoryEvent.Target> targets = new ArrayList<>();
    targets.add(new AppRecordHistoryEvent.Target(formName, recordId, TARGET_SELF));
    LinkedHashSet<String> seen = new LinkedHashSet<>();
    for (List<Map<String, Object>> parents : List.of(parentsBefore, parentsAfter)) {
      for (Map<String, Object> relation : parents) {
        String parentForm = text(relation, "form");
        String parentRecord = text(relation, "recordId");
        if (seen.add(parentForm + "\u0000" + parentRecord)) {
          targets.add(new AppRecordHistoryEvent.Target(parentForm, parentRecord, TARGET_PARENT));
        }
      }
    }
    return targets;
  }

  private Map<String, Object> contextOf(Object... pairs) {
    Map<String, Object> context = new LinkedHashMap<>();
    for (int index = 0; index + 1 < pairs.length; index += 2) {
      if (pairs[index + 1] != null) context.put(String.valueOf(pairs[index]), pairs[index + 1]);
    }
    return context;
  }

  static String digest(String... parts) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      for (String part : parts) {
        digest.update((part == null ? "" : part).getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (RuntimeException | java.security.NoSuchAlgorithmException error) {
      throw new IllegalStateException("生成历史去重键失败", error);
    }
  }

  /**
   * 事件构建器：一次操作复制完成后生成不可变事件副本。
   */
  static class EventBuilder {
    private String appId;
    private String eventKey;
    private String operationId;
    private String formName;
    private String formLabel;
    private String recordId;
    private String recordLabel;
    private String eventType;
    private String source = SOURCE_APPS;
    private String actorType = ACTOR_USER;
    private String actorId;
    private String actorName;
    private LocalDateTime occurredAt;
    private List<Map<String, Object>> changes;
    private Map<String, Object> context;
    private List<AppRecordHistoryEvent.Target> targets = List.of();

    EventBuilder appId(String value) {
      this.appId = value;
      return this;
    }

    EventBuilder eventKey(String value) {
      this.eventKey = value;
      return this;
    }

    EventBuilder operationId(String value) {
      this.operationId = value;
      return this;
    }

    EventBuilder formName(String value) {
      this.formName = value;
      return this;
    }

    EventBuilder formLabel(String value) {
      this.formLabel = value;
      return this;
    }

    EventBuilder recordId(String value) {
      this.recordId = value;
      return this;
    }

    EventBuilder recordLabel(String value) {
      this.recordLabel = value;
      return this;
    }

    EventBuilder eventType(String value) {
      this.eventType = value;
      return this;
    }

    EventBuilder source(String value) {
      this.source = value;
      return this;
    }

    EventBuilder actor(String type, String id, String name) {
      this.actorType = type;
      this.actorId = id;
      this.actorName = name;
      return this;
    }

    EventBuilder occurredAt(LocalDateTime value) {
      this.occurredAt = value;
      return this;
    }

    EventBuilder changes(List<Map<String, Object>> value) {
      this.changes = value;
      return this;
    }

    EventBuilder context(Map<String, Object> value) {
      this.context = value;
      return this;
    }

    EventBuilder targets(List<AppRecordHistoryEvent.Target> value) {
      this.targets = value;
      return this;
    }

    AppRecordHistoryEvent build() {
      return new AppRecordHistoryEvent(appId, eventKey, operationId, formName, formLabel, recordId,
        recordLabel, eventType, source, actorType, actorId, actorName, occurredAt, changes, context, targets);
    }
  }
}
