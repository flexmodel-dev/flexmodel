package dev.flexmodel.apps;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.apps.dto.AppFormFieldResponse;
import dev.flexmodel.apps.dto.AppFormResponse;
import dev.flexmodel.common.NotFoundException;
import dev.flexmodel.common.SessionContext;
import dev.flexmodel.common.ValidationException;
import dev.flexmodel.common.dto.PageDTO;
import dev.flexmodel.data.DataService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 应用表单记录服务。对外仅暴露表单名称，内部映射为实体记录操作。
 */
@ApplicationScoped
public class AppRecordService {

  private static final Set<String> BUILTIN_FIELDS = Set.of(
    "id", "created_by", "updated_by", "created_at", "updated_at");
  private static final Set<String> SEARCHABLE_TYPES = Set.of(
    "TEXT", "NTEXT", "PHONE", "EMAIL", "URL", "SERIES", "PICKLIST", "SELECT",
    "CLASSIFICATION", "TAG", "FILE", "IMAGE", "AVATAR", "BARCODE", "LOCATION",
    "SIGN", "STATE", "REFERENCE");
  private static final int MAX_SIZE = 200;

  @Inject
  AppFormService appFormService;

  @Inject
  DataService dataService;

  @Inject
  SessionContext sessionContext;

  public PageDTO<Map<String, Object>> findRecords(
    String appId,
    String formName,
    int page,
    int size,
    String search,
    String sortField,
    String sortOrder,
    String mainFormName,
    String mainRecordId,
    String referenceField
  ) {
    ResolvedForm form = resolveForm(appId, formName);
    RecordContext context = resolveRecordContext(
      appId, form, mainFormName, mainRecordId, referenceField);
    int normalizedPage = Math.max(page, 1);
    int normalizedSize = Math.clamp(size, 1, MAX_SIZE);
    String filter = buildFilter(form.response(), search, context);
    String sort = buildSort(form.response(), sortField, sortOrder);
    return dataService.findPagingRecords(
      appId, form.entityName(), normalizedPage, normalizedSize, filter, sort, null);
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
    return context == null
      ? requireRecord(appId, form.entityName(), recordId)
      : requireSubformRecord(appId, form.entityName(), recordId, context);
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
    LocalDateTime now = LocalDateTime.now();
    String userId = currentUserId();
    values.put("id", UUID.randomUUID().toString());
    values.put("created_by", userId);
    values.put("updated_by", userId);
    values.put("created_at", now);
    values.put("updated_at", now);
    dataService.createRecord(appId, form.entityName(), values);
    return requireRecord(appId, form.entityName(), String.valueOf(values.get("id")));
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
      requireRecord(appId, form.entityName(), recordId);
    } else {
      requireSubformRecord(appId, form.entityName(), recordId, context);
    }
    Map<String, Object> values = sanitizeInput(form.response(), input, false, context);
    values.put("updated_by", currentUserId());
    values.put("updated_at", LocalDateTime.now());
    dataService.updateRecord(appId, form.entityName(), recordId, values);
    return requireRecord(appId, form.entityName(), recordId);
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
      requireRecord(appId, form.entityName(), recordId);
    } else {
      requireSubformRecord(appId, form.entityName(), recordId, context);
    }
    dataService.deleteRecord(appId, form.entityName(), recordId);
  }

  private ResolvedForm resolveForm(String appId, String formName) {
    AppFormResponse response = appFormService.findForm(appId, formName);
    String entityName = appFormService.resolveEntityName(appId, formName);
    return new ResolvedForm(response, entityName);
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
    requireRecord(appId, mainForm.entityName(), mainRecordId.trim());
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
      return candidates.stream()
        .filter(field -> field.name().equals(normalized))
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
      if (field.required() && isEmpty(value)) {
        throw new ValidationException("必填字段不能为空: " + field.label());
      }
      values.put(name, value);
    });
    if (context != null) {
      values.put(context.referenceField(), context.mainRecordId());
    }
    if (creating) {
      fields.values().forEach(field -> {
        if (!field.builtin() && field.required() && isEmpty(values.get(field.name()))) {
          throw new ValidationException("必填字段不能为空: " + field.label());
        }
      });
    }
    return values;
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

  private String buildFilter(
    AppFormResponse form,
    String search,
    RecordContext context
  ) {
    Map<String, Object> searchFilter = buildSearchFilter(form, search);
    if (context == null) {
      return searchFilter == null ? null : JsonUtils.toJsonString(searchFilter);
    }
    Map<String, Object> relationFilter = Map.of(
      context.referenceField(), Map.of("_eq", context.mainRecordId()));
    if (searchFilter == null) {
      return JsonUtils.toJsonString(relationFilter);
    }
    return JsonUtils.toJsonString(Map.of(
      "_and", List.of(relationFilter, searchFilter)));
  }

  private String buildSort(AppFormResponse form, String sortField, String sortOrder) {
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

  private Map<String, Object> requireRecord(String appId, String entityName, String recordId) {
    Map<String, Object> record = dataService.findOneRecord(appId, entityName, recordId, null);
    if (record == null) {
      throw new NotFoundException("记录不存在: " + recordId);
    }
    return record;
  }

  private Map<String, Object> requireSubformRecord(
    String appId,
    String entityName,
    String recordId,
    RecordContext context
  ) {
    Map<String, Object> record = requireRecord(appId, entityName, recordId);
    Object value = record.get(context.referenceField());
    if (!context.mainRecordId().equals(value == null ? null : String.valueOf(value))) {
      throw new NotFoundException("记录不存在: " + recordId);
    }
    return record;
  }

  private boolean isEmpty(Object value) {
    if (value == null) {
      return true;
    }
    if (value instanceof String text) {
      return text.isBlank();
    }
    return false;
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
