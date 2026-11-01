package dev.flexmodel.apps.view;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.apps.form.AppFieldRules;
import dev.flexmodel.apps.form.AppFormService;
import dev.flexmodel.apps.form.dto.*;
import dev.flexmodel.apps.record.AppRecordService;
import dev.flexmodel.apps.view.dto.*;
import dev.flexmodel.common.ValidationException;
import dev.flexmodel.common.dto.PageDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 表单多类型视图查询与拖拽写入。
 */
@ApplicationScoped
public class AppViewService {

  private static final Set<String> SCOPES = Set.of(
    "RECORDS", "GROUPS", "GROUP_RECORDS", "RANGE", "UNSCHEDULED", "TREE_ROOTS", "TREE_CHILDREN");
  private static final Set<String> GROUP_FIELD_TYPES = Set.of(
    "PICKLIST", "SELECT", "CLASSIFICATION", "BOOL", "REFERENCE");
  private static final Set<String> DATE_TYPES = Set.of("DATE", "DATETIME");
  private static final int DEFAULT_SIZE = 30;
  private static final int MAX_SIZE = 200;
  private static final int DEFAULT_RANGE_SIZE = 1000;
  private static final int MAX_RANGE_SIZE = 5000;
  private static final int GROUP_LIMIT = 500;
  private static final int MAX_TREE_NODES = 10000;
  private static final int DEFAULT_TREE_SIZE = 50;

  @Inject
  AppFormService appFormService;

  @Inject
  AppRecordService appRecordService;

  public AppViewQueryResponse query(
    String appId,
    String formName,
    String viewId,
    AppViewQueryRequest request
  ) {
    AppFormResponse form = appFormService.findForm(appId, formName);
    AppFormViewTabRecord view = requireView(form, viewId);
    AppViewQueryRequest normalized = request == null
      ? new AppViewQueryRequest(null, null, null, null, null, null, null, null, null, null, null, null)
      : request;
    String scope = normalizeScope(normalized.scope());
    String filter = baseFilter(form, view, normalized);
    return switch (scope) {
      case "GROUPS" -> groups(appId, form, view, filter);
      case "GROUP_RECORDS" -> groupRecords(appId, form, view, filter, normalized);
      case "RANGE" -> range(appId, form, view, filter, normalized);
      case "UNSCHEDULED" -> unscheduled(appId, form, view, filter, normalized);
      case "TREE_ROOTS", "TREE_CHILDREN" -> tree(appId, form, view, filter, normalized, scope);
      default -> records(appId, form, view, filter, normalized);
    };
  }

  public Map<String, Object> move(
    String appId,
    String formName,
    String viewId,
    String recordId,
    AppViewMoveRequest request
  ) {
    AppFormResponse form = appFormService.findForm(appId, formName);
    AppFormViewTabRecord view = requireView(form, viewId);
    if (request == null || request.changes() == null || request.changes().isEmpty()) {
      throw new ValidationException("拖拽未产生字段变化");
    }
    String type = viewType(view);
    AppViewConfig config = config(view);
    Map<String, AppViewFieldChange> changes = request.changes();
    Map<String, Object> expected = new LinkedHashMap<>();
    Map<String, Object> values = new LinkedHashMap<>();
    switch (type) {
      case "KANBAN" -> {
        AppFormFieldResponse groupField = requireGroupField(form, config);
        rejectUnknownChanges(changes, Set.of(groupField.name()));
        AppViewFieldChange change = changes.get(groupField.name());
        if (change == null) {
          throw new ValidationException("看板拖拽必须修改分组字段");
        }
        expected.put(groupField.name(), change.from());
        values.put(groupField.name(), normalizeGroupValue(appId, groupField, change.to()));
      }
      case "CALENDAR" -> {
        AppFormFieldResponse startField = requireDateField(form, config.startField(), "日历开始字段");
        AppFormFieldResponse endField = optionalDateField(form, config.endField(), "日历结束字段", startField.type());
        rejectUnknownChanges(changes, Set.of(startField.name()));
        AppViewFieldChange change = changes.get(startField.name());
        if (change == null) {
          throw new ValidationException("日历拖拽必须修改开始时间");
        }
        Map<String, Object> current = appRecordService.requireRecordForView(appId, form, recordId);
        Object oldStartValue = current.get(startField.name());
        if (isEmpty(oldStartValue)) {
          throw new ValidationException("记录缺少开始时间，无法拖拽");
        }
        String newStart = requireTemporalValue(startField, change.to());
        expected.put(startField.name(), change.from());
        values.put(startField.name(), newStart);
        if (endField != null && !isEmpty(current.get(endField.name()))) {
          values.put(endField.name(), moveTemporalEnd(
            startField, oldStartValue, newStart, current.get(endField.name())));
        }
      }
      case "GANTT" -> {
        AppFormFieldResponse startField = requireDateField(form, config.startField(), "甘特开始字段");
        AppFormFieldResponse endField = requireDateField(form, config.endField(), "甘特结束字段");
        if (!startField.type().equals(endField.type())) {
          throw new ValidationException("甘特起止字段类型不一致");
        }
        rejectUnknownChanges(changes, Set.of(startField.name(), endField.name()));
        Map<String, Object> current = appRecordService.requireRecordForView(appId, form, recordId);
        String mode = normalizeMoveMode(request.mode());
        changes.forEach((fieldName, change) -> expected.put(fieldName, change.from()));
        applyGanttMove(startField, endField, mode, changes, current, values);
      }
      default -> throw new ValidationException("当前视图不支持拖拽");
    }
    values.replaceAll((fieldName, value) -> AppFieldRules.validateValue(
      requireField(form, fieldName), value));
    return appRecordService.moveViewRecord(appId, form, recordId, expected, values);
  }

  // ------------------------------------------------------------------ 查询范围

  private AppViewQueryResponse records(
    String appId,
    AppFormResponse form,
    AppFormViewTabRecord view,
    String filter,
    AppViewQueryRequest request
  ) {
    String sort = resolveSort(form, view, request);
    int page = page(request);
    int size = size(request, DEFAULT_SIZE, MAX_SIZE);
    PageDTO<Map<String, Object>> result = appRecordService.queryViewRecords(
      appId, form, page, size, filter, sort, viewExtraFields(view));
    return response(form, view, result.list(), result.total(), null, null, false);
  }

  private AppViewQueryResponse groups(
    String appId,
    AppFormResponse form,
    AppFormViewTabRecord view,
    String filter
  ) {
    AppViewConfig config = config(view);
    AppFormFieldResponse groupField = requireGroupField(form, config);
    List<Map<String, Object>> rows = appRecordService.aggregateViewRecords(
      appId, form, groupField.name(), filter, GROUP_LIMIT);
    LinkedHashMap<String, Long> counts = new LinkedHashMap<>();
    long ungrouped = 0;
    for (Map<String, Object> row : rows) {
      Object dimension = row.get("dimension");
      long count = row.get("value") instanceof Number number ? number.longValue() : 0;
      if (dimension == null || String.valueOf(dimension).isBlank()) {
        ungrouped += count;
      } else {
        counts.merge(String.valueOf(dimension), count, Long::sum);
      }
    }
    List<AppViewGroup> groups = switch (groupField.type()) {
      case "BOOL" -> booleanGroups(counts);
      case "PICKLIST", "SELECT", "CLASSIFICATION" -> optionGroups(groupField, counts);
      case "REFERENCE" -> referenceGroups(appId, form, groupField, counts);
      default -> new ArrayList<>();
    };
    if (ungrouped > 0) {
      groups.add(new AppViewGroup(null, "未分组", null, ungrouped, true));
    }
    long total = groups.stream().mapToLong(AppViewGroup::total).sum();
    return response(form, view, List.of(), total, groups, null, false);
  }

  private AppViewQueryResponse groupRecords(
    String appId,
    AppFormResponse form,
    AppFormViewTabRecord view,
    String filter,
    AppViewQueryRequest request
  ) {
    AppViewConfig config = config(view);
    AppFormFieldResponse groupField = requireGroupField(form, config);
    boolean emptyGroup = Boolean.TRUE.equals(request.groupEmpty()) || request.groupValue() == null;
    Object groupValue = emptyGroup ? null : normalizeGroupValue(appId, groupField, request.groupValue());
    String combined = andCondition(filter, condition(groupField.name(), "_eq", groupValue));
    String sort = resolveSort(form, view, request);
    PageDTO<Map<String, Object>> result = appRecordService.queryViewRecords(
      appId, form, page(request), size(request, DEFAULT_SIZE, MAX_SIZE), combined, sort,
      viewExtraFields(view));
    return response(form, view, result.list(), result.total(), null, null, false);
  }

  private AppViewQueryResponse range(
    String appId,
    AppFormResponse form,
    AppFormViewTabRecord view,
    String filter,
    AppViewQueryRequest request
  ) {
    AppViewConfig config = config(view);
    AppFormFieldResponse startField = requireDateField(form, config.startField(), "视图开始字段");
    AppFormFieldResponse endField = optionalDateField(form, config.endField(), "视图结束字段", startField.type());
    String rangeStart = requireRangeText(request.rangeStart(), "范围开始");
    String rangeEnd = requireRangeText(request.rangeEnd(), "范围结束");
    validateRangeOrder(startField, rangeStart, rangeEnd);
    boolean gantt = "GANTT".equals(viewType(view));
    String combined = andCondition(filter,
      rangeCondition(startField, endField, rangeStart, rangeEnd, gantt));
    int page = page(request);
    int size = size(request, DEFAULT_RANGE_SIZE, MAX_RANGE_SIZE);
    PageDTO<Map<String, Object>> result = appRecordService.queryViewRecords(
      appId, form, page, size, combined, resolveSort(form, view, request), viewExtraFields(view));
    markInvalidRanges(result.list(), startField, endField);
    boolean hasMore = (long) page * size < result.total();
    return response(form, view, result.list(), result.total(), null, null, hasMore);
  }

  private AppViewQueryResponse unscheduled(
    String appId,
    AppFormResponse form,
    AppFormViewTabRecord view,
    String filter,
    AppViewQueryRequest request
  ) {
    AppViewConfig config = config(view);
    AppFormFieldResponse startField = requireDateField(form, config.startField(), "视图开始字段");
    AppFormFieldResponse endField = optionalDateField(form, config.endField(), "视图结束字段", startField.type());
    List<Object> conditions = new ArrayList<>();
    conditions.add(condition(startField.name(), "_eq", null));
    if (endField != null) {
      Map<String, Object> invalidRange = new LinkedHashMap<>();
      invalidRange.put(endField.name(), Map.of("_lt", Map.of("_field", startField.name())));
      invalidRange.putAll(condition(startField.name(), "_ne", null));
      conditions.add(invalidRange);
      if ("GANTT".equals(viewType(view))) {
        conditions.add(condition(endField.name(), "_eq", null));
      }
    }
    String combined = andCondition(filter, Map.of("_or", conditions));
    PageDTO<Map<String, Object>> result = appRecordService.queryViewRecords(
      appId, form, page(request), size(request, DEFAULT_SIZE, MAX_SIZE), combined,
      resolveSort(form, view, request), viewExtraFields(view));
    markInvalidRanges(result.list(), startField, endField);
    return response(form, view, result.list(), result.total(), null, null, false);
  }

  // ------------------------------------------------------------------ 层级

  private AppViewQueryResponse tree(
    String appId,
    AppFormResponse form,
    AppFormViewTabRecord view,
    String filter,
    AppViewQueryRequest request,
    String scope
  ) {
    AppViewConfig config = config(view);
    AppFormFieldResponse parentField = requireHierarchyParent(form, config);
    String sort = resolveSort(form, view, request);
    PageDTO<Map<String, Object>> all = appRecordService.queryViewRecords(
      appId, form, 1, MAX_TREE_NODES, filter, sort, viewExtraFields(view));
    if (all.total() > MAX_TREE_NODES) {
      throw new ValidationException("层级筛选结果超过 " + MAX_TREE_NODES + " 条，请缩小筛选范围");
    }
    List<Map<String, Object>> records = all.list();
    LinkedHashMap<String, Map<String, Object>> byId = new LinkedHashMap<>();
    List<String> order = new ArrayList<>();
    for (Map<String, Object> record : records) {
      String id = String.valueOf(record.get("id"));
      byId.put(id, record);
      order.add(id);
    }
    LinkedHashMap<String, String> parents = new LinkedHashMap<>();
    Set<String> anomalies = new LinkedHashSet<>();
    for (String id : order) {
      String parentId = referenceId(byId.get(id).get(parentField.name()));
      if (parentId != null && parentId.equals(id)) {
        anomalies.add(id);
        parents.put(id, null);
      } else if (parentId != null && byId.containsKey(parentId)) {
        parents.put(id, parentId);
      } else {
        parents.put(id, null);
      }
    }
    breakCycles(order, parents, anomalies);
    Map<String, List<String>> children = new LinkedHashMap<>();
    for (String id : order) {
      String parentId = parents.get(id);
      if (parentId != null) {
        children.computeIfAbsent(parentId, ignored -> new ArrayList<>()).add(id);
      }
    }
    List<String> candidates = new ArrayList<>();
    if ("TREE_ROOTS".equals(scope)) {
      for (String id : order) {
        if (parents.get(id) == null) {
          candidates.add(id);
        }
      }
    } else {
      String parentId = request.parentId();
      if (parentId == null || parentId.isBlank() || !byId.containsKey(parentId.trim())) {
        candidates = List.of();
      } else {
        candidates = children.getOrDefault(parentId.trim(), List.of());
      }
    }
    int page = Math.max(request.page() == null ? 1 : request.page(), 1);
    int size = size(request, DEFAULT_TREE_SIZE, MAX_SIZE);
    int from = Math.min((page - 1) * size, candidates.size());
    int to = Math.min(from + size, candidates.size());
    List<Map<String, Object>> pageRecords = new ArrayList<>();
    LinkedHashMap<String, Boolean> childFlags = new LinkedHashMap<>();
    for (String id : candidates.subList(from, to)) {
      Map<String, Object> record = byId.get(id);
      if (anomalies.contains(id)) {
        record.put("_anomaly", true);
      }
      pageRecords.add(record);
      childFlags.put(id, !children.getOrDefault(id, List.of()).isEmpty());
    }
    return response(form, view, pageRecords, candidates.size(), null, childFlags, false);
  }

  void breakCycles(
    List<String> order,
    LinkedHashMap<String, String> parents,
    Set<String> anomalies
  ) {
    Set<String> settled = new HashSet<>();
    for (String id : order) {
      if (settled.contains(id)) {
        continue;
      }
      List<String> path = new ArrayList<>();
      Map<String, Integer> positions = new HashMap<>();
      String current = id;
      while (current != null && !settled.contains(current) && !positions.containsKey(current)) {
        positions.put(current, path.size());
        path.add(current);
        current = parents.get(current);
      }
      if (current != null && positions.containsKey(current)) {
        List<String> cycle = path.subList(positions.get(current), path.size());
        cycle.forEach(anomalies::add);
        String representative = cycle.stream().min(String::compareTo).orElse(cycle.getFirst());
        parents.put(representative, null);
      }
      settled.addAll(path);
    }
  }

  // ------------------------------------------------------------------ 拖拽计算

  private void applyGanttMove(
    AppFormFieldResponse startField,
    AppFormFieldResponse endField,
    String mode,
    Map<String, AppViewFieldChange> changes,
    Map<String, Object> current,
    Map<String, Object> values
  ) {
    AppViewFieldChange startChange = changes.get(startField.name());
    AppViewFieldChange endChange = changes.get(endField.name());
    Object oldStart = current.get(startField.name());
    Object oldEnd = current.get(endField.name());
    if (isEmpty(oldStart) || isEmpty(oldEnd)) {
      throw new ValidationException("记录缺少开始或结束时间，请先补充排期");
    }
    switch (mode) {
      case "RESIZE_START" -> {
        if (startChange == null) {
          throw new ValidationException("拉伸开始时间缺少目标值");
        }
        String newStart = requireTemporalValue(startField, startChange.to());
        ensureEndAfterStart(startField, newStart, String.valueOf(oldEnd));
        values.put(startField.name(), newStart);
      }
      case "RESIZE_END" -> {
        if (endChange == null) {
          throw new ValidationException("拉伸结束时间缺少目标值");
        }
        String newEnd = requireTemporalValue(endField, endChange.to());
        ensureEndAfterStart(startField, String.valueOf(oldStart), newEnd);
        values.put(endField.name(), newEnd);
      }
      default -> {
        if (startChange != null) {
          String newStart = requireTemporalValue(startField, startChange.to());
          values.put(startField.name(), newStart);
          if (endChange != null) {
            String newEnd = requireTemporalValue(endField, endChange.to());
            ensureEndAfterStart(startField, newStart, newEnd);
            values.put(endField.name(), newEnd);
          } else {
            values.put(endField.name(), moveTemporalEnd(startField, oldStart, newStart, oldEnd));
          }
        } else if (endChange != null) {
          String newEnd = requireTemporalValue(endField, endChange.to());
          ensureEndAfterStart(startField, String.valueOf(oldStart), newEnd);
          values.put(endField.name(), newEnd);
        } else {
          throw new ValidationException("甘特拖拽未提交起止时间");
        }
      }
    }
  }

  String moveTemporalEnd(
    AppFormFieldResponse field,
    Object oldStartValue,
    String newStartValue,
    Object oldEndValue
  ) {
    if ("DATE".equals(field.type())) {
      LocalDate oldStart = parseDate(String.valueOf(oldStartValue), field.label());
      LocalDate newStart = parseDate(newStartValue, field.label());
      LocalDate oldEnd = parseDate(String.valueOf(oldEndValue), field.label());
      LocalDate newEnd = oldEnd.plusDays(ChronoUnit.DAYS.between(oldStart, newStart));
      if (newEnd.isBefore(newStart)) {
        throw new ValidationException("结束日期不能早于开始日期");
      }
      return newEnd.toString();
    }
    LocalDateTime oldStart = parseDateTime(String.valueOf(oldStartValue), field.label());
    LocalDateTime newStart = parseDateTime(newStartValue, field.label());
    LocalDateTime oldEnd = parseDateTime(String.valueOf(oldEndValue), field.label());
    LocalDateTime newEnd = oldEnd.plusSeconds(ChronoUnit.SECONDS.between(oldStart, newStart));
    ensureEndAfterStart(field, newStart.toString(), newEnd.toString());
    return newEnd.toString();
  }

  void ensureEndAfterStart(
    AppFormFieldResponse startField,
    String startValue,
    String endValue
  ) {
    if ("DATE".equals(startField.type())) {
      LocalDate start = parseDate(startValue, startField.label());
      LocalDate end = parseDate(endValue, startField.label());
      if (end.isBefore(start)) {
        throw new ValidationException("结束日期不能早于开始日期");
      }
      return;
    }
    LocalDateTime start = parseDateTime(startValue, startField.label());
    LocalDateTime end = parseDateTime(endValue, startField.label());
    if (!end.isAfter(start)) {
      throw new ValidationException("结束时间必须晚于开始时间");
    }
  }

  private Object normalizeGroupValue(
    String appId,
    AppFormFieldResponse groupField,
    Object value
  ) {
    if (value == null) {
      return null;
    }
    return switch (groupField.type()) {
      case "BOOL" -> {
        if (value instanceof Boolean bool) {
          yield bool;
        }
        String text = String.valueOf(value);
        if ("true".equalsIgnoreCase(text) || "1".equals(text)) {
          yield true;
        }
        if ("false".equalsIgnoreCase(text) || "0".equals(text)) {
          yield false;
        }
        throw new ValidationException("看板分组值不合法: " + text);
      }
      case "PICKLIST", "SELECT", "CLASSIFICATION" -> {
        String optionId = String.valueOf(value);
        boolean exists = groupField.options() == null || groupField.options().isEmpty()
          || groupField.options().stream().anyMatch(option -> optionId.equals(option.id()));
        if (!exists) {
          throw new ValidationException("看板分组选项不存在: " + optionId);
        }
        yield optionId;
      }
      case "REFERENCE" -> {
        String recordId = String.valueOf(value);
        if (groupField.referenceForm() != null && !groupField.referenceForm().isBlank()) {
          appRecordService.requireRecordExists(appId, groupField.referenceForm(), recordId);
        }
        yield recordId;
      }
      default -> throw new ValidationException("看板分组字段类型不支持拖拽: " + groupField.label());
    };
  }

  // ------------------------------------------------------------------ 分组构建

  List<AppViewGroup> booleanGroups(Map<String, Long> counts) {
    Map<Boolean, Long> resolved = new HashMap<>();
    resolved.put(true, 0L);
    resolved.put(false, 0L);
    counts.forEach((key, value) -> {
      if ("true".equalsIgnoreCase(key) || "1".equals(key)) {
        resolved.merge(true, value, Long::sum);
      } else if ("false".equalsIgnoreCase(key) || "0".equals(key)) {
        resolved.merge(false, value, Long::sum);
      }
    });
    return new ArrayList<>(List.of(
      new AppViewGroup(true, "是", null, resolved.get(true), false),
      new AppViewGroup(false, "否", null, resolved.get(false), false)));
  }

  List<AppViewGroup> optionGroups(
    AppFormFieldResponse field,
    Map<String, Long> counts
  ) {
    List<AppViewGroup> groups = new ArrayList<>();
    Set<String> used = new LinkedHashSet<>();
    if (field.options() != null) {
      for (AppFormFieldOptionResponse option : field.options()) {
        String id = option.id();
        String label = option.text() == null || option.text().isBlank() ? id : option.text();
        groups.add(new AppViewGroup(id, label, option.color(), counts.getOrDefault(id, 0L), false));
        used.add(id);
      }
    }
    counts.forEach((key, value) -> {
      if (!used.contains(key)) {
        groups.add(new AppViewGroup(key, key, null, value, false));
      }
    });
    return groups;
  }

  private List<AppViewGroup> referenceGroups(
    String appId,
    AppFormResponse form,
    AppFormFieldResponse field,
    Map<String, Long> counts
  ) {
    Map<String, String> labels = appRecordService.referenceLabels(
      appId, form, field.name(), counts.keySet());
    List<Map.Entry<String, Long>> entries = new ArrayList<>(counts.entrySet());
    entries.sort((left, right) -> {
      int compared = Long.compare(right.getValue(), left.getValue());
      return compared != 0 ? compared : left.getKey().compareTo(right.getKey());
    });
    List<AppViewGroup> groups = new ArrayList<>();
    for (Map.Entry<String, Long> entry : entries) {
      groups.add(new AppViewGroup(
        entry.getKey(), labels.getOrDefault(entry.getKey(), entry.getKey()), null, entry.getValue(), false));
    }
    return groups;
  }

  // ------------------------------------------------------------------ 过滤与排序

  private String baseFilter(
    AppFormResponse form,
    AppFormViewTabRecord view,
    AppViewQueryRequest request
  ) {
    String transientFilter = request.filter() == null || request.filter().isEmpty()
      ? null
      : JsonUtils.toJsonString(request.filter());
    return appRecordService.buildFilter(form, request.search(), view.id(), null, transientFilter);
  }

  Map<String, Object> rangeCondition(
    AppFormFieldResponse startField,
    AppFormFieldResponse endField,
    String rangeStart,
    String rangeEnd,
    boolean gantt
  ) {
    List<Object> conditions = new ArrayList<>();
    conditions.add(condition(startField.name(), "_lte", rangeEnd));
    conditions.add(condition(startField.name(), "_ne", null));
    if (endField == null) {
      conditions.add(condition(startField.name(), "_gte", rangeStart));
      return Map.of("_and", conditions);
    }
    // 非法范围单独进入未排期列表，不参与时间轴展示；日历允许结束值为空。
    if (gantt) {
      conditions.add(Map.of(endField.name(), Map.of("_gte", Map.of("_field", startField.name()))));
      conditions.add(condition(endField.name(), "_ne", null));
      conditions.add(condition(endField.name(), "_gte", rangeStart));
      return Map.of("_and", conditions);
    }
    conditions.add(Map.of("_or", List.of(
      condition(endField.name(), "_eq", null),
      Map.of(endField.name(), Map.of("_gte", Map.of("_field", startField.name()))))));
    // 日历事件只要与窗口相交即可；未设置结束时间的事件按当天事件处理。
    conditions.add(Map.of("_or", List.of(
      condition(endField.name(), "_gte", rangeStart),
      Map.of("_and", List.of(
        condition(endField.name(), "_eq", null),
        condition(startField.name(), "_gte", rangeStart))))));
    return Map.of("_and", conditions);
  }

  private Map<String, Object> condition(String fieldName, String operator, Object value) {
    Map<String, Object> operatorMap = new LinkedHashMap<>();
    operatorMap.put(operator, value);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put(fieldName, operatorMap);
    return result;
  }

  private String andCondition(String baseFilter, Map<String, Object> condition) {
    if (baseFilter == null || baseFilter.isBlank()) {
      return JsonUtils.toJsonString(condition);
    }
    Map<String, Object> base = JsonUtils.parseToMap(baseFilter);
    return JsonUtils.toJsonString(Map.of("_and", List.of(base, condition)));
  }

  private String resolveSort(
    AppFormResponse form,
    AppFormViewTabRecord view,
    AppViewQueryRequest request
  ) {
    String field = request.sortField();
    String order = request.sortOrder();
    if ((field == null || field.isBlank()) && view.sort() != null) {
      field = view.sort().field();
      order = view.sort().order();
    }
    return appRecordService.buildSort(form, field, order);
  }

  private void markInvalidRanges(
    List<Map<String, Object>> records,
    AppFormFieldResponse startField,
    AppFormFieldResponse endField
  ) {
    if (endField == null) {
      return;
    }
    for (Map<String, Object> record : records) {
      Object start = record.get(startField.name());
      Object end = record.get(endField.name());
      if (isEmpty(start) || isEmpty(end)) {
        continue;
      }
      try {
        if ("DATE".equals(startField.type())) {
          if (parseDate(String.valueOf(end), endField.label())
            .isBefore(parseDate(String.valueOf(start), startField.label()))) {
            record.put("_invalidRange", true);
          }
        } else if (!parseDateTime(String.valueOf(end), endField.label())
          .isAfter(parseDateTime(String.valueOf(start), startField.label()))) {
          record.put("_invalidRange", true);
        }
      } catch (RuntimeException ignored) {
        record.put("_invalidRange", true);
      }
    }
  }

  private void validateRangeOrder(
    AppFormFieldResponse field,
    String rangeStart,
    String rangeEnd
  ) {
    if ("DATE".equals(field.type())) {
      LocalDate start = parseDate(rangeStart, "范围开始");
      LocalDate end = parseDate(rangeEnd, "范围结束");
      if (end.isBefore(start)) {
        throw new ValidationException("范围结束不能早于范围开始");
      }
      return;
    }
    LocalDateTime start = parseDateTime(rangeStart, "范围开始");
    LocalDateTime end = parseDateTime(rangeEnd, "范围结束");
    if (!end.isAfter(start)) {
      throw new ValidationException("范围结束必须晚于范围开始");
    }
  }

  // ------------------------------------------------------------------ 通用

  private AppViewQueryResponse response(
    AppFormResponse form,
    AppFormViewTabRecord view,
    List<Map<String, Object>> list,
    long total,
    List<AppViewGroup> groups,
    Map<String, Boolean> childFlags,
    boolean hasMore
  ) {
    Movement movement = movement(form, view);
    return new AppViewQueryResponse(
      view.id(), viewType(view), list, total, groups, childFlags, hasMore,
      movement.movable(), movement.disabledReason());
  }

  private Movement movement(AppFormResponse form, AppFormViewTabRecord view) {
    String type = viewType(view);
    List<String> fields = switch (type) {
      case "KANBAN" -> List.of(config(view).groupField());
      case "CALENDAR" -> List.of(config(view).startField());
      case "GANTT" -> List.of(config(view).startField(), config(view).endField());
      default -> List.of();
    };
    if (fields.isEmpty() || fields.stream().anyMatch(name -> name == null || name.isBlank())) {
      return new Movement(false, "视图尚未配置拖拽字段");
    }
    for (String name : fields) {
      AppFormFieldResponse field = form.fields().stream()
        .filter(item -> item.name().equals(name))
        .findFirst()
        .orElse(null);
      if (field == null) {
        return new Movement(false, "视图配置字段不存在: " + name);
      }
      if (field.builtin()) {
        return new Movement(false, "内置字段不支持拖拽: " + field.label());
      }
      if (!AppFieldRules.writable(field, false)) {
        return new Movement(false, "字段不允许修改: " + field.label());
      }
    }
    return new Movement(true, null);
  }

  private Set<String> viewExtraFields(AppFormViewTabRecord view) {
    LinkedHashSet<String> fields = new LinkedHashSet<>();
    if (view.displayFields() != null) {
      fields.addAll(view.displayFields());
    }
    AppViewConfig config = config(view);
    addIfPresent(fields, config.groupField());
    addIfPresent(fields, config.titleField());
    addIfPresent(fields, config.coverField());
    addIfPresent(fields, config.startField());
    addIfPresent(fields, config.endField());
    addIfPresent(fields, config.parentField());
    return fields;
  }

  private void addIfPresent(Set<String> fields, String value) {
    if (value != null && !value.isBlank()) {
      fields.add(value);
    }
  }

  private AppFormViewTabRecord requireView(AppFormResponse form, String viewId) {
    String normalized = viewId == null ? "" : viewId.trim();
    return (form.viewTabs() == null ? List.<AppFormViewTabRecord>of() : form.viewTabs()).stream()
      .filter(tab -> tab != null && normalized.equals(tab.id()))
      .findFirst()
      .orElseThrow(() -> new ValidationException("视图不存在: " + normalized));
  }

  private String normalizeScope(String scope) {
    if (scope == null || scope.isBlank()) {
      return "RECORDS";
    }
    String normalized = scope.trim().toUpperCase(Locale.ROOT);
    if (!SCOPES.contains(normalized)) {
      throw new ValidationException("视图查询范围不合法: " + scope);
    }
    return normalized;
  }

  private String normalizeMoveMode(String mode) {
    if (mode == null || mode.isBlank()) {
      return "MOVE";
    }
    String normalized = mode.trim().toUpperCase(Locale.ROOT);
    if (!Set.of("MOVE", "RESIZE_START", "RESIZE_END").contains(normalized)) {
      throw new ValidationException("拖拽方式不合法: " + mode);
    }
    return normalized;
  }

  private String viewType(AppFormViewTabRecord view) {
    return view.type() == null || view.type().isBlank() ? "TABLE" : view.type();
  }

  private AppViewConfig config(AppFormViewTabRecord view) {
    return view.config() == null ? AppViewConfig.empty() : view.config();
  }

  private AppFormFieldResponse requireGroupField(AppFormResponse form, AppViewConfig config) {
    AppFormFieldResponse field = requireField(form, config.groupField(), "看板分组字段");
    if (!GROUP_FIELD_TYPES.contains(field.type())) {
      throw new ValidationException("看板分组字段类型不支持: " + field.label());
    }
    return field;
  }

  private AppFormFieldResponse requireHierarchyParent(AppFormResponse form, AppViewConfig config) {
    AppFormFieldResponse field = requireField(form, config.parentField(), "层级父节点字段");
    if (!"REFERENCE".equals(field.type()) || !form.name().equals(field.referenceForm())) {
      throw new ValidationException("层级父节点字段必须关联本表单: " + field.label());
    }
    return field;
  }

  private AppFormFieldResponse requireDateField(AppFormResponse form, String fieldName, String label) {
    AppFormFieldResponse field = requireField(form, fieldName, label);
    if (!DATE_TYPES.contains(field.type())) {
      throw new ValidationException(label + "必须是日期或日期时间字段: " + field.label());
    }
    return field;
  }

  private AppFormFieldResponse optionalDateField(
    AppFormResponse form,
    String fieldName,
    String label,
    String expectedType
  ) {
    if (fieldName == null || fieldName.isBlank()) {
      return null;
    }
    AppFormFieldResponse field = requireDateField(form, fieldName, label);
    if (expectedType != null && !expectedType.equals(field.type())) {
      throw new ValidationException(label + "必须与开始字段同为" + ("DATE".equals(expectedType) ? "日期" : "日期时间"));
    }
    return field;
  }

  private AppFormFieldResponse requireField(AppFormResponse form, String fieldName, String label) {
    if (fieldName == null || fieldName.isBlank()) {
      throw new ValidationException(label + "不能为空");
    }
    return requireField(form, fieldName.trim());
  }

  private AppFormFieldResponse requireField(AppFormResponse form, String fieldName) {
    return form.fields().stream()
      .filter(field -> field.name().equals(fieldName))
      .findFirst()
      .orElseThrow(() -> new ValidationException("字段不存在: " + fieldName));
  }

  private void rejectUnknownChanges(
    Map<String, AppViewFieldChange> changes,
    Set<String> allowedFields
  ) {
    changes.keySet().forEach(fieldName -> {
      if (!allowedFields.contains(fieldName)) {
        throw new ValidationException("当前视图不允许拖拽修改字段: " + fieldName);
      }
    });
  }

  private String requireTemporalValue(AppFormFieldResponse field, Object value) {
    if (value == null || String.valueOf(value).isBlank()) {
      throw new ValidationException(field.label() + "不能为空");
    }
    String text = String.valueOf(value).trim();
    if ("DATE".equals(field.type())) {
      return parseDate(text, field.label()).toString();
    }
    return parseDateTime(text, field.label()).toString();
  }

  private LocalDate parseDate(String value, String label) {
    try {
      return LocalDate.parse(value.length() > 10 ? value.substring(0, 10) : value);
    } catch (DateTimeParseException error) {
      throw new ValidationException(label + "日期格式不正确");
    }
  }

  private LocalDateTime parseDateTime(String value, String label) {
    try {
      return LocalDateTime.parse(value.replace(' ', 'T'));
    } catch (DateTimeParseException error) {
      throw new ValidationException(label + "日期时间格式不正确");
    }
  }

  private String requireRangeText(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new ValidationException(label + "不能为空");
    }
    return value.trim();
  }

  private String referenceId(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Map<?, ?> map) {
      Object id = map.get("id");
      return id == null ? null : String.valueOf(id);
    }
    if (value instanceof Collection<?> collection) {
      return collection.isEmpty() ? null : referenceId(collection.iterator().next());
    }
    String text = String.valueOf(value);
    return text.isBlank() ? null : text;
  }

  private boolean isEmpty(Object value) {
    return value == null || value instanceof String text && text.isBlank();
  }

  private int page(AppViewQueryRequest request) {
    return request.page() == null ? 1 : Math.max(request.page(), 1);
  }

  private int size(AppViewQueryRequest request, int defaultSize, int maxSize) {
    if (request.size() == null) {
      return defaultSize;
    }
    return Math.clamp(request.size(), 1, maxSize);
  }

  private record Movement(boolean movable, String disabledReason) {
  }
}
