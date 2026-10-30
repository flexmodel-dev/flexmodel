package dev.flexmodel.apps;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.apps.dto.AppFormFieldResponse;
import dev.flexmodel.apps.dto.AppFormItemRecord;
import dev.flexmodel.apps.dto.AppFormResponse;
import dev.flexmodel.apps.dto.AppFormViewTabRecord;
import dev.flexmodel.apps.dto.AppReferenceValueResponse;
import dev.flexmodel.common.NotFoundException;
import dev.flexmodel.common.ProjectUnitOfWork;
import dev.flexmodel.common.config.EngineConfig;
import dev.flexmodel.common.SessionContext;
import dev.flexmodel.common.ValidationException;
import dev.flexmodel.common.dto.PageDTO;
import dev.flexmodel.data.DataService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 应用表单记录服务。对外仅暴露表单名称，内部映射为实体记录操作。
 */
@ApplicationScoped
@Slf4j
public class AppRecordService {

  private static final Set<String> BUILTIN_FIELDS = Set.of(
    "id", "created_by", "updated_by", "created_at", "updated_at");
  private static final Set<String> SEARCHABLE_TYPES = Set.of(
    "TEXT", "NTEXT", "PHONE", "EMAIL", "URL", "SERIES", "PICKLIST", "SELECT",
    "CLASSIFICATION", "TAG", "FILE", "IMAGE", "AVATAR", "BARCODE", "LOCATION",
    "SIGN", "STATE", "REFERENCE");
  private static final int MAX_SIZE = 200;
  private static final String SYSTEM_SCHEMA = EngineConfig.SYSTEM_DS_KEY;

  @Inject
  AppFormService appFormService;

  @Inject
  DataService dataService;

  @Inject
  SessionContext sessionContext;

  @Inject
  AppRecordDiscussionRepository appRecordDiscussionRepository;

  @Inject
  AppRecordHistoryRecorder appRecordHistoryRecorder;

  @Inject
  ProjectUnitOfWork transactions;

  public PageDTO<Map<String, Object>> findRecords(
    String appId,
    String formName,
    int page,
    int size,
    String search,
    String sortField,
    String sortOrder,
    String viewTabId,
    String mainFormName,
    String mainRecordId,
    String referenceField,
    String queryFilter
  ) {
    ResolvedForm form = resolveForm(appId, formName);
    RecordContext context = resolveRecordContext(
      appId, form, mainFormName, mainRecordId, referenceField);
    int normalizedPage = Math.max(page, 1);
    int normalizedSize = Math.clamp(size, 1, MAX_SIZE);
    String filter = buildFilter(form.response(), search, viewTabId, context, queryFilter);
    String sort = buildSort(form.response(), sortField, sortOrder);
    PageDTO<Map<String, Object>> result = findPagingRecords(
      appId, form.response(), normalizedPage, normalizedSize, filter, sort,
      referenceFields(form.response()));
    renderReferenceValues(appId, form.response(), result);
    return projectRecords(form.response(), result);
  }

  public Map<String, Object> findRecord(
    String appId,
    String formName,
    String recordId,
    String mainFormName,
    String mainRecordId,
    String referenceField
  ) {
    ResolvedForm form = resolveForm(appId, formName);
    RecordContext context = resolveRecordContext(
      appId, form, mainFormName, mainRecordId, referenceField);
    List<String> expand = referenceFields(form.response());
    Map<String, Object> record = context == null
      ? requireRecord(appId, form, recordId, expand)
      : requireSubformRecord(appId, form, recordId, context, expand);
    return projectRecord(form.response(), renderReferenceRecord(
      appId, form.response(), record));
  }

  /**
   * 供记录讨论等旁路能力校验记录存在，避免加载引用展开。
   */
  public void requireRecordExists(String appId, String formName, String recordId) {
    ResolvedForm form = resolveForm(appId, formName);
    requireRecord(appId, form, recordId);
  }

  public Map<String, Object> createRecord(
    String appId,
    String formName,
    Map<String, Object> input,
    String mainFormName,
    String mainRecordId,
    String referenceField
  ) {
    ResolvedForm form = resolveForm(appId, formName);
    RecordContext context = resolveRecordContext(
      appId, form, mainFormName, mainRecordId, referenceField);
    Map<String, Object> values = sanitizeInput(form.response(), input, true, context);
    assignCreationFields(form.response(), values, currentUserId());
    createRecord(appId, form.response(), values);
    if (historyCaptureEnabled(appId, form.response())) {
      appRecordHistoryRecorder.created(appId, form.response(), values);
    }
    return projectRecord(form.response(), renderReferenceRecord(
      appId, form.response(), requireRecord(
        appId, form, String.valueOf(values.get("id")), referenceFields(form.response()))));
  }

  public Map<String, Object> updateRecord(
    String appId,
    String formName,
    String recordId,
    Map<String, Object> input,
    String mainFormName,
    String mainRecordId,
    String referenceField
  ) {
    ResolvedForm form = resolveForm(appId, formName);
    RecordContext context = resolveRecordContext(
      appId, form, mainFormName, mainRecordId, referenceField);
    if (context == null) {
      requireRecord(appId, form, recordId);
    } else {
      requireSubformRecord(appId, form, recordId, context, null);
    }
    Map<String, Object> values = sanitizeInput(form.response(), input, false, context);
    values.put("updated_by", currentUserId());
    values.put("updated_at", LocalDateTime.now());
    if (historyCaptureEnabled(appId, form.response())) {
      // 行锁 + 事务内前后值读取，保证并发修改的历史前值不被污染
      transactions.execute(appId, () -> {
        ProjectUnitOfWork.lock(appId, form.entityName(), "id", recordId);
        Map<String, Object> before = context == null
          ? requireRecord(appId, form, recordId)
          : requireSubformRecord(appId, form, recordId, context, null);
        updateRecord(appId, form.response(), recordId, values);
        Map<String, Object> after = requireRecord(appId, form, recordId);
        appRecordHistoryRecorder.updated(appId, form.response(), recordId, before, after);
        return null;
      });
    } else {
      updateRecord(appId, form.response(), recordId, values);
    }
    return projectRecord(form.response(), renderReferenceRecord(
      appId, form.response(), requireRecord(
        appId, form, recordId, referenceFields(form.response()))));
  }

  public void deleteRecord(
    String appId,
    String formName,
    String recordId,
    String mainFormName,
    String mainRecordId,
    String referenceField
  ) {
    ResolvedForm form = resolveForm(appId, formName);
    RecordContext context = resolveRecordContext(
      appId, form, mainFormName, mainRecordId, referenceField);
    if (context == null) {
      requireRecord(appId, form, recordId);
    } else {
      requireSubformRecord(appId, form, recordId, context, null);
    }
    if (historyCaptureEnabled(appId, form.response())) {
      transactions.execute(appId, () -> {
        ProjectUnitOfWork.lock(appId, form.entityName(), "id", recordId);
        Map<String, Object> before = context == null
          ? requireRecord(appId, form, recordId)
          : requireSubformRecord(appId, form, recordId, context, null);
        deleteRecord(appId, form.response(), recordId);
        appRecordHistoryRecorder.deleted(appId, form.response(), recordId, before);
        return null;
      });
    } else {
      deleteRecord(appId, form.response(), recordId);
    }
    try {
      appRecordDiscussionRepository.deleteByRecord(appId, form.response().name(), recordId);
    } catch (RuntimeException error) {
      log.warn("Failed to clean record discussions for {}/{}/{}: {}", appId, form.response().name(), recordId, error.getMessage());
    }
  }

  private boolean historyCaptureEnabled(String appId, AppFormResponse form) {
    return appRecordHistoryRecorder != null && transactions != null && !form.builtin()
      && appRecordHistoryRecorder.captureSupported(appId);
  }

  private ResolvedForm resolveForm(String appId, String formName) {
    AppFormResponse response = appFormService.findForm(appId, formName);
    return new ResolvedForm(response, response.name());
  }

  private RecordContext resolveRecordContext(
    String appId,
    ResolvedForm childForm,
    String mainFormName,
    String mainRecordId,
    String referenceField
  ) {
    boolean hasMainForm = mainFormName != null && !mainFormName.isBlank();
    boolean hasMainRecord = mainRecordId != null && !mainRecordId.isBlank();
    if (hasMainForm != hasMainRecord) {
      throw new ValidationException("主表单与主记录ID必须同时提供");
    }
    if (!hasMainForm) {
      return null;
    }
    ResolvedForm mainForm = resolveForm(appId, mainFormName.trim());
    requireRecord(appId, mainForm, mainRecordId.trim());
    String resolvedReferenceField = resolveReferenceField(
      childForm.response(), mainFormName.trim(), referenceField);
    return new RecordContext(mainRecordId.trim(), resolvedReferenceField);
  }

  private String resolveReferenceField(
    AppFormResponse childForm,
    String mainFormName,
    String referenceField
  ) {
    List<AppFormFieldResponse> candidates = childForm.fields().stream()
      .filter(field -> "REFERENCE".equals(field.type())
        && mainFormName.equals(field.referenceForm()))
      .toList();
    if (referenceField != null && !referenceField.isBlank()) {
      String normalized = referenceField.trim();
      return childForm.fields().stream()
        .filter(field -> field.name().equals(normalized))
        .filter(field -> !field.builtin())
        .findFirst()
        .orElseThrow(() -> new ValidationException("子表单引用字段不合法: " + normalized))
        .name();
    }
    if (candidates.size() == 1) {
      return candidates.getFirst().name();
    }
    if (candidates.isEmpty()) {
      throw new ValidationException("子表单缺少指向主表单的引用字段");
    }
    throw new ValidationException("子表单存在多个引用字段，请指定referenceField");
  }

  private List<String> referenceFields(AppFormResponse form) {
    return form.fields().stream()
      .filter(field -> "REFERENCE".equals(field.type()))
      .map(AppFormFieldResponse::name)
      .toList();
  }

  private PageDTO<Map<String, Object>> renderReferenceValues(
    String appId,
    AppFormResponse form,
    PageDTO<Map<String, Object>> page
  ) {
    if (page == null || page.list() == null || page.list().isEmpty()) {
      return page;
    }
    form.fields().stream()
      .filter(field -> "REFERENCE".equals(field.type()))
      .filter(field -> field.referenceForm() != null && !field.referenceForm().isBlank())
      .forEach(field -> renderReferenceField(appId, field, page.list()));
    return page;
  }

  private Map<String, Object> renderReferenceRecord(
    String appId,
    AppFormResponse form,
    Map<String, Object> record
  ) {
    renderReferenceValues(appId, form, new PageDTO<>(List.of(record), 1L));
    return record;
  }

  private void renderReferenceField(
    String appId,
    AppFormFieldResponse field,
    List<Map<String, Object>> records
  ) {
    ResolvedForm referenceForm = resolveForm(appId, field.referenceForm());
    Map<Object, Map<String, Object>> referenceRecords = new LinkedHashMap<>();
    records.forEach(record -> collectExpandedReferenceRecords(
      record.get(field.name()), referenceRecords));

    LinkedHashSet<Object> missingIds = new LinkedHashSet<>();
    records.forEach(record -> collectMissingReferenceIds(
      record.get(field.name()), referenceRecords, missingIds));
    if (!missingIds.isEmpty()) {
      String filter = JsonUtils.toJsonString(Map.of(
        "id", Map.of("_in", List.copyOf(missingIds))));
      PageDTO<Map<String, Object>> referencePage = findPagingRecords(
        appId, referenceForm.response(), 1, missingIds.size(), filter, null, null);
      referencePage.list().forEach(record -> {
        if (record.get("id") != null) {
          referenceRecords.put(record.get("id"), record);
        }
      });
    }

    records.forEach(record -> record.put(
      field.name(), referenceValue(field, referenceForm, record.get(field.name()), referenceRecords)));
  }

  private PageDTO<Map<String, Object>> projectRecords(
    AppFormResponse form,
    PageDTO<Map<String, Object>> page
  ) {
    if (page == null || page.list() == null) {
      return page;
    }
    Set<String> exposedFields = exposedFields(form);
    List<Map<String, Object>> records = page.list().stream()
      .map(record -> projectRecord(record, exposedFields))
      .toList();
    return new PageDTO<>(records, page.total());
  }

  private Map<String, Object> projectRecord(
    AppFormResponse form,
    Map<String, Object> record
  ) {
    return projectRecord(record, exposedFields(form));
  }

  private Map<String, Object> projectRecord(
    Map<String, Object> record,
    Set<String> exposedFields
  ) {
    if (record == null) {
      return null;
    }
    Map<String, Object> projected = new LinkedHashMap<>();
    if (record.containsKey("id")) {
      projected.put("id", record.get("id"));
    }
    exposedFields.forEach(name -> {
      if (record.containsKey(name)) {
        projected.put(name, record.get(name));
      }
    });
    return projected;
  }

  Set<String> exposedFields(AppFormResponse form) {
    if (form.layout() == null || form.layout().items() == null) {
      return Set.of();
    }
    LinkedHashSet<String> fields = new LinkedHashSet<>();
    for (AppFormItemRecord item : form.layout().items()) {
      if (item == null) {
        continue;
      }
      if ("FIELD".equals(item.type()) && item.name() != null && !item.name().isBlank()) {
        fields.add(item.name());
      } else if ("SUBFORM".equals(item.type())
        && item.referenceField() != null && !item.referenceField().isBlank()) {
        fields.add(item.referenceField());
      }
    }
    return Collections.unmodifiableSet(fields);
  }

  private void collectExpandedReferenceRecords(
    Object value,
    Map<Object, Map<String, Object>> referenceRecords
  ) {
    if (value instanceof Map<?, ?> record) {
      Object id = record.get("id");
      if (id != null) {
        Map<String, Object> typedRecord = new LinkedHashMap<>();
        record.forEach((key, entryValue) -> typedRecord.put(String.valueOf(key), entryValue));
        referenceRecords.put(id, typedRecord);
      }
      return;
    }
    if (value instanceof List<?> values) {
      values.forEach(item -> collectExpandedReferenceRecords(item, referenceRecords));
    }
  }

  private void collectMissingReferenceIds(
    Object value,
    Map<Object, Map<String, Object>> referenceRecords,
    LinkedHashSet<Object> missingIds
  ) {
    if (value == null || value instanceof Map<?, ?>) {
      return;
    }
    if (value instanceof List<?> values) {
      values.forEach(item -> collectMissingReferenceIds(item, referenceRecords, missingIds));
      return;
    }
    if (!value.toString().isBlank() && !referenceRecords.containsKey(value)) {
      missingIds.add(value);
    }
  }

  private Object referenceValue(
    AppFormFieldResponse field,
    ResolvedForm referenceForm,
    Object value,
    Map<Object, Map<String, Object>> referenceRecords
  ) {
    if (value == null || value.equals("")) {
      return value;
    }
    if (value instanceof List<?> values) {
      return values.stream()
        .map(item -> referenceValue(field, referenceForm, item, referenceRecords))
        .toList();
    }

    Map<String, Object> targetRecord;
    Object id;
    if (value instanceof Map<?, ?> expandedRecord) {
      targetRecord = new LinkedHashMap<>();
      expandedRecord.forEach((key, entryValue) -> targetRecord.put(String.valueOf(key), entryValue));
      id = expandedRecord.get("id");
    } else {
      id = value;
      targetRecord = referenceRecords.get(value);
    }
    if (id == null) {
      return null;
    }
    return new AppReferenceValueResponse(
      String.valueOf(id),
      displayValue(targetRecord, referenceForm.response(), id),
      field.referenceForm());
  }

  private String displayValue(
    Map<String, Object> record,
    AppFormResponse form,
    Object id
  ) {
    if (record == null) {
      return String.valueOf(id);
    }
    String displayField = form.displayField();
    if (displayField == null || displayField.isBlank()) {
      return String.valueOf(record.getOrDefault("id", id));
    }
    Object value = record.get(displayField);
    if (value == null || value.equals("") || (value instanceof String text && text.isBlank())) {
      return String.valueOf(record.getOrDefault("id", id));
    }
    if (value instanceof Map<?, ?> referencedRecord && referencedRecord.get("id") != null) {
      return String.valueOf(referencedRecord.get("id"));
    }
    return String.valueOf(value);
  }

  private Map<String, Object> sanitizeInput(
    AppFormResponse form,
    Map<String, Object> input,
    boolean creating,
    RecordContext context
  ) {
    if (input == null) {
      throw new ValidationException("记录数据不能为空");
    }
    Map<String, AppFormFieldResponse> fields = new LinkedHashMap<>();
    form.fields().forEach(field -> fields.put(field.name(), field));
    Map<String, Object> values = new LinkedHashMap<>();
    if (context != null && input.containsKey(context.referenceField())) {
      throw new ValidationException("子表单引用字段由系统维护: " + context.referenceField());
    }
    input.forEach((name, value) -> {
      AppFormFieldResponse field = fields.get(name);
      if (field == null) {
        throw new ValidationException("记录字段不存在: " + name);
      }
      if (field.builtin()) {
        throw new ValidationException("内置字段不允许写入: " + name);
      }
      if (!AppFieldRules.writable(field, creating)) {
        throw new ValidationException("字段不允许" + (creating ? "新建时填写: " : "修改: ") + field.label());
      }
      if (field.required() && AppFieldRules.isEmpty(value)) {
        throw new ValidationException("必填字段不能为空: " + field.label());
      }
      values.put(name, AppFieldRules.validateValue(field, value));
    });
    if (context != null) {
      values.put(context.referenceField(), context.mainRecordId());
    }
    if (creating) {
      fields.values().forEach(field -> {
        if (!field.builtin() && !values.containsKey(field.name())) {
          Object defaultValue = AppFieldRules.defaultValue(field);
          if (defaultValue != null) values.put(field.name(), AppFieldRules.validateValue(field, defaultValue));
        }
        if (!field.builtin() && field.required() && AppFieldRules.isEmpty(values.get(field.name()))) {
          throw new ValidationException("必填字段不能为空: " + field.label());
        }
      });
    }
    return values;
  }

  Map<String, Object> prepareImport(AppFormResponse form, Map<String, Object> input) {
    return sanitizeInput(form, input, true, null);
  }

  void assignCreationFields(AppFormResponse form, Map<String, Object> values, String userId) {
    LocalDateTime now = LocalDateTime.now();
    if (!form.builtin()) values.put("owner_id", userId);
    values.put("created_by", userId);
    values.put("updated_by", userId);
    values.put("created_at", now);
    values.put("updated_at", now);
  }

  private Map<String, Object> buildSearchFilter(AppFormResponse form, String search) {
    if (search == null || search.isBlank()) {
      return null;
    }
    String keyword = search.trim();
    if (keyword.length() > 100) {
      throw new ValidationException("搜索关键字长度不能超过100");
    }
    List<Object> conditions = new ArrayList<>();
    form.fields().stream()
      .filter(field -> !field.builtin() && SEARCHABLE_TYPES.contains(field.type()))
      .forEach(field -> {
        Map<String, Object> condition = new LinkedHashMap<>();
        condition.put(field.name(), Map.of("_contains", keyword));
        conditions.add(condition);
      });
    if (conditions.isEmpty()) {
      return null;
    }
    Map<String, Object> filter = new LinkedHashMap<>();
    filter.put("_or", conditions);
    return filter;
  }

  String buildFilter(
    AppFormResponse form,
    String search,
    String viewTabId,
    RecordContext context,
    String queryFilter
  ) {
    Map<String, Object> searchFilter = buildSearchFilter(form, search);
    Map<String, Object> viewTabFilter = resolveViewTabFilter(form, viewTabId);
    Map<String, Object> relationFilter = context == null ? null : Map.of(
      context.referenceField(), Map.of("_eq", context.mainRecordId()));
    List<Map<String, Object>> filters = new ArrayList<>();
    Map<String, Object> advanced = appFormService.normalizeQueryFilter(form, queryFilter);
    if (advanced != null) filters.add(advanced);
    if (viewTabFilter != null) {
      filters.add(viewTabFilter);
    }
    if (relationFilter != null) {
      filters.add(relationFilter);
    }
    if (searchFilter != null) {
      filters.add(searchFilter);
    }
    if (filters.isEmpty()) {
      return null;
    }
    if (filters.size() == 1) {
      return JsonUtils.toJsonString(filters.getFirst());
    }
    return JsonUtils.toJsonString(Map.of("_and", filters));
  }

  private Map<String, Object> resolveViewTabFilter(AppFormResponse form, String viewTabId) {
    if (viewTabId == null || viewTabId.isBlank() || "all".equals(viewTabId.trim())) {
      return null;
    }
    String normalizedViewTabId = viewTabId.trim();
    return (form.viewTabs() == null ? List.<AppFormViewTabRecord>of() : form.viewTabs()).stream()
      .filter(tab -> tab != null && normalizedViewTabId.equals(tab.id()))
      .findFirst()
      .map(tab -> {
        if (tab.filter() == null) {
          throw new ValidationException("视图页签筛选条件不能为空");
        }
        return tab.filter();
      })
      .orElseThrow(() -> new ValidationException("视图页签不存在: " + normalizedViewTabId));
  }

  String buildSort(AppFormResponse form, String sortField, String sortOrder) {
    String normalizedSortOrder = normalizeSortOrder(sortOrder);
    String normalizedSortField = sortField == null || sortField.isBlank() ? "created_at" : sortField.trim();
    if (!BUILTIN_FIELDS.contains(normalizedSortField)) {
      boolean exists = form.fields().stream()
        .anyMatch(field -> field.name().equals(normalizedSortField));
      if (!exists) {
        throw new ValidationException("排序字段不存在: " + normalizedSortField);
      }
    }
    return JsonUtils.toJsonString(List.of(Map.of(
      "field", normalizedSortField,
      "order", normalizedSortOrder
    )));
  }

  private String normalizeSortOrder(String sortOrder) {
    if (sortOrder == null || sortOrder.isBlank()) {
      return "DESC";
    }
    return switch (sortOrder.trim().toUpperCase()) {
      case "ASC" -> "ASC";
      case "DESC" -> "DESC";
      default -> throw new ValidationException("排序方向不合法: " + sortOrder);
    };
  }

  private PageDTO<Map<String, Object>> findPagingRecords(
    String appId,
    AppFormResponse form,
    int page,
    int size,
    String filter,
    String sort,
    List<String> expand
  ) {
    if (form.builtin()) {
      return dataService.findPagingRecords(
        appId, SYSTEM_SCHEMA, form.name(), page, size, filter, sort, expand);
    }
    return dataService.findPagingRecords(
      appId, form.name(), page, size, filter, sort, expand);
  }

  private Map<String, Object> findOneRecord(
    String appId,
    AppFormResponse form,
    String recordId,
    List<String> expand
  ) {
    if (form.builtin()) {
      return dataService.findOneRecord(
        appId, SYSTEM_SCHEMA, form.name(), recordId, expand);
    }
    return dataService.findOneRecord(appId, form.name(), recordId, expand);
  }

  private Map<String, Object> createRecord(
    String appId,
    AppFormResponse form,
    Map<String, Object> values
  ) {
    if (form.builtin()) {
      return dataService.createRecord(appId, SYSTEM_SCHEMA, form.name(), values);
    }
    return dataService.createRecord(appId, form.name(), values);
  }

  private Map<String, Object> updateRecord(
    String appId,
    AppFormResponse form,
    String recordId,
    Map<String, Object> values
  ) {
    if (form.builtin()) {
      return dataService.updateRecord(
        appId, SYSTEM_SCHEMA, form.name(), recordId, values);
    }
    return dataService.updateRecord(appId, form.name(), recordId, values);
  }

  private void deleteRecord(
    String appId,
    AppFormResponse form,
    String recordId
  ) {
    if (form.builtin()) {
      dataService.deleteRecord(appId, SYSTEM_SCHEMA, form.name(), recordId);
      return;
    }
    dataService.deleteRecord(appId, form.name(), recordId);
  }

  private Map<String, Object> requireRecord(
    String appId,
    ResolvedForm form,
    String recordId,
    List<String> expand
  ) {
    Map<String, Object> record = findOneRecord(appId, form.response(), recordId, expand);
    if (record == null) {
      throw new NotFoundException("记录不存在: " + recordId);
    }
    return record;
  }

  private Map<String, Object> requireRecord(String appId, ResolvedForm form, String recordId) {
    return requireRecord(appId, form, recordId, null);
  }

  private Map<String, Object> requireSubformRecord(
    String appId,
    ResolvedForm form,
    String recordId,
    RecordContext context,
    List<String> expand
  ) {
    Map<String, Object> record = requireRecord(appId, form, recordId, expand);
    Object value = record.get(context.referenceField());
    if (!context.mainRecordId().equals(referenceRecordId(value))) {
      throw new NotFoundException("记录不存在: " + recordId);
    }
    return record;
  }

  private String referenceRecordId(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof List<?> values && !values.isEmpty()) {
      return referenceRecordId(values.getFirst());
    }
    if (value instanceof Map<?, ?> record) {
      Object id = record.get("id");
      return id == null ? null : String.valueOf(id);
    }
    return String.valueOf(value);
  }

  private String currentUserId() {
    String userId = sessionContext.getUserId();
    return userId == null || userId.isBlank() ? "admin" : userId;
  }

  private record ResolvedForm(AppFormResponse response, String entityName) {
  }

  private record RecordContext(String mainRecordId, String referenceField) {
  }
}
