package dev.flexmodel.apps;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.apps.dto.AppFormFieldResponse;
import dev.flexmodel.apps.dto.AppFormResponse;
import dev.flexmodel.common.NotFoundException;
import dev.flexmodel.common.SessionContext;
import dev.flexmodel.common.ValidationException;
import dev.flexmodel.common.dto.PageDTO;
import dev.flexmodel.data.DataService;
import dev.flexmodel.model.Direction;
import dev.flexmodel.query.Query;
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

  private static final Set<String> SYSTEM_FIELDS = Set.of(
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
    String sortOrder
  ) {
    ResolvedForm form = resolveForm(appId, formName);
    int normalizedPage = Math.max(page, 1);
    int normalizedSize = Math.min(Math.max(size, 1), MAX_SIZE);
    String filter = buildSearchFilter(form.response(), search);
    String sort = buildSort(form.response(), sortField, sortOrder);
    List<Map<String, Object>> records = dataService.findPagingRecords(
      appId, form.entityName(), normalizedPage, normalizedSize, filter, sort, null);
    long total = dataService.countRecords(appId, form.entityName(), filter);
    return new PageDTO<>(records, total);
  }

  public Map<String, Object> findRecord(String appId, String formName, String recordId) {
    ResolvedForm form = resolveForm(appId, formName);
    Map<String, Object> record = dataService.findOneRecord(appId, form.entityName(), recordId, null);
    if (record == null) {
      throw new NotFoundException("记录不存在: " + recordId);
    }
    return record;
  }

  public Map<String, Object> createRecord(String appId, String formName, Map<String, Object> input) {
    ResolvedForm form = resolveForm(appId, formName);
    Map<String, Object> values = sanitizeInput(form.response(), input, true);
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
    Map<String, Object> input
  ) {
    ResolvedForm form = resolveForm(appId, formName);
    requireRecord(appId, form.entityName(), recordId);
    Map<String, Object> values = sanitizeInput(form.response(), input, false);
    values.put("updated_by", currentUserId());
    values.put("updated_at", LocalDateTime.now());
    dataService.updateRecord(appId, form.entityName(), recordId, values);
    return requireRecord(appId, form.entityName(), recordId);
  }

  public void deleteRecord(String appId, String formName, String recordId) {
    ResolvedForm form = resolveForm(appId, formName);
    requireRecord(appId, form.entityName(), recordId);
    dataService.deleteRecord(appId, form.entityName(), recordId);
  }

  private ResolvedForm resolveForm(String appId, String formName) {
    AppFormResponse response = appFormService.findForm(appId, formName);
    String entityName = appFormService.resolveEntityName(appId, formName);
    return new ResolvedForm(response, entityName);
  }

  private Map<String, Object> sanitizeInput(
    AppFormResponse form,
    Map<String, Object> input,
    boolean creating
  ) {
    if (input == null) {
      throw new ValidationException("记录数据不能为空");
    }
    Map<String, AppFormFieldResponse> fields = new LinkedHashMap<>();
    form.fields().forEach(field -> fields.put(field.name(), field));
    Map<String, Object> values = new LinkedHashMap<>();
    input.forEach((name, value) -> {
      AppFormFieldResponse field = fields.get(name);
      if (field == null) {
        throw new ValidationException("记录字段不存在: " + name);
      }
      if (field.system()) {
        throw new ValidationException("系统字段不允许写入: " + name);
      }
      if (field.required() && isEmpty(value)) {
        throw new ValidationException("必填字段不能为空: " + field.label());
      }
      values.put(name, value);
    });
    if (creating) {
      fields.values().forEach(field -> {
        if (!field.system() && field.required() && isEmpty(values.get(field.name()))) {
          throw new ValidationException("必填字段不能为空: " + field.label());
        }
      });
    }
    return values;
  }

  private String buildSearchFilter(AppFormResponse form, String search) {
    if (search == null || search.isBlank()) {
      return null;
    }
    String keyword = search.trim();
    if (keyword.length() > 100) {
      throw new ValidationException("搜索关键字长度不能超过100");
    }
    List<Object> conditions = new ArrayList<>();
    form.fields().stream()
      .filter(field -> !field.system() && SEARCHABLE_TYPES.contains(field.type()))
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
    return JsonUtils.toJsonString(filter);
  }

  private String buildSort(AppFormResponse form, String sortField, String sortOrder) {
    String normalizedSortOrder = normalizeSortOrder(sortOrder);
    String normalizedSortField = sortField == null || sortField.isBlank() ? "created_at" : sortField.trim();
    if (!SYSTEM_FIELDS.contains(normalizedSortField)) {
      boolean exists = form.fields().stream()
        .anyMatch(field -> field.name().equals(normalizedSortField));
      if (!exists) {
        throw new ValidationException("排序字段不存在: " + normalizedSortField);
      }
    }
    Query.OrderBy.Sort sort = new Query.OrderBy.Sort();
    sort.setField(normalizedSortField);
    sort.setOrder(Direction.valueOf(normalizedSortOrder));
    return JsonUtils.toJsonString(List.of(sort));
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
}