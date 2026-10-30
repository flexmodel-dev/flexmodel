package dev.flexmodel.apps;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.apps.dto.*;
import dev.flexmodel.codegen.entity.AppWorkbench;
import dev.flexmodel.codegen.entity.Project;
import dev.flexmodel.common.NotFoundException;
import dev.flexmodel.common.SessionContext;
import dev.flexmodel.common.ValidationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDateTime;
import java.util.*;

@ApplicationScoped
public class WorkbenchService {

  private static final String DEFAULT_NAME = "默认工作台";
  private static final String DATA_ENDPOINT_TEMPLATE =
    "/api/apps/{appId}/workbenches/{workbenchId}/components/{componentId}/data";
  private static final int GRID_COLUMNS = 12;
  private static final int MAX_GRID_HEIGHT = 24;
  private static final Set<String> CHART_TYPES = Set.of("line", "bar", "pie");
  private static final Set<String> AGGREGATIONS = Set.of("count", "sum", "avg");
  private static final Set<String> PALETTES = Set.of("primary", "multi");
  private static final Set<String> MEASURE_FIELD_TYPES = Set.of("NUMBER", "DECIMAL");
  private static final Set<String> GROUPABLE_FIELD_TYPES = Set.of(
    "TEXT", "NTEXT", "PHONE", "EMAIL", "URL", "SERIES", "PICKLIST", "SELECT",
    "NUMBER", "DECIMAL", "DATE", "DATETIME", "TIME", "CLASSIFICATION", "BOOL", "STATE");

  @Inject
  WorkbenchRepository workbenchRepository;

  @Inject
  AppNavRecordStore appNavRecordStore;

  @Inject
  AppFormService appFormService;

  @Inject
  SessionContext sessionContext;

  public List<WorkbenchSummaryResponse> findWorkbenches(String appId) {
    Project project = appNavRecordStore.requireAppProject(appId);
    List<AppWorkbench> workbenches = workbenchRepository.findByProjectId(project.getId());
    if (workbenches.isEmpty()) {
      AppWorkbench created = createEntity(project.getId(), DEFAULT_NAME, null, WorkbenchLayoutRecord.empty());
      workbenches = List.of(created);
    }
    return workbenches.stream().map(this::toSummary).toList();
  }

  public WorkbenchResponse findWorkbench(String appId, String workbenchId) {
    return toResponse(requireWorkbench(appId, workbenchId));
  }

  public WorkbenchComponentRecord findComponent(String appId, String workbenchId, String componentId) {
    AppWorkbench workbench = requireWorkbench(appId, workbenchId);
    WorkbenchLayoutRecord layout = validatedLayout(appId, toLayout(workbench));
    return layout.components().stream()
      .filter(component -> Objects.equals(component.id(), componentId))
      .findFirst()
      .orElseThrow(() -> new NotFoundException("工作台组件不存在: " + componentId));
  }

  public WorkbenchCapabilityResponse findCapabilities(String appId) {
    Project project = appNavRecordStore.requireAppProject(appId);
    List<AppFormResponse> forms = appFormService.findForms(appId);
    List<AppNavRecord> navs = appNavRecordStore.navRecords(project);

    return new WorkbenchCapabilityResponse(
      WorkbenchLayoutRecord.CURRENT_SCHEMA_VERSION,
      componentSpecs(),
      forms.stream().map(this::toFormOption).toList(),
      shortcutNavOptions(navs),
      availableValues(),
      exampleLayout());
  }

  public WorkbenchResponse createWorkbench(String appId, WorkbenchCreateRequest request) {
    Project project = appNavRecordStore.requireAppProject(appId);
    String name = validatedName(request.name());
    validateDescription(request.description());
    requireUniqueName(project.getId(), name, null);
    WorkbenchLayoutRecord layout = request.layout() == null
      ? WorkbenchLayoutRecord.empty()
      : validatedLayout(appId, request.layout());
    AppWorkbench workbench = createEntity(project.getId(), name, request.description(), layout);
    return toResponse(workbench);
  }

  public WorkbenchResponse patchWorkbench(String appId, String workbenchId, WorkbenchPatchRequest request) {
    Project project = appNavRecordStore.requireAppProject(appId);
    AppWorkbench workbench = requireWorkbench(project.getId(), workbenchId);

    if (request.hasName()) {
      String name = validatedName(request.name());
      requireUniqueName(project.getId(), name, workbench.getId());
      workbench.setName(name);
    }
    if (request.hasDescription()) {
      validateDescription(request.description());
      workbench.setDescription(request.description());
    }
    if (request.hasLayout()) {
      WorkbenchLayoutRecord layout = validatedLayout(appId, request.layout());
      workbench.setLayout(toLayoutValue(layout));
    }
    if (request.hasName() || request.hasDescription() || request.hasLayout()) {
      workbench.setUpdatedBy(currentUserId());
      workbench.setUpdatedAt(LocalDateTime.now());
      workbenchRepository.save(workbench);
    }
    return toResponse(workbench);
  }

  public void deleteWorkbench(String appId, String workbenchId) {
    Project project = appNavRecordStore.requireAppProject(appId);
    AppWorkbench workbench = requireWorkbench(project.getId(), workbenchId);
    if (workbenchRepository.countByProjectId(project.getId()) <= 1) {
      throw new ValidationException("至少保留一个工作台");
    }
    workbenchRepository.deleteByProjectIdAndId(project.getId(), workbenchId);
  }

  private AppWorkbench createEntity(
    String projectId,
    String name,
    String description,
    WorkbenchLayoutRecord layout
  ) {
    LocalDateTime now = LocalDateTime.now();
    String userId = currentUserId();
    AppWorkbench workbench = new AppWorkbench();
    workbench.setId(UUID.randomUUID().toString());
    workbench.setProjectId(projectId);
    workbench.setName(name);
    workbench.setDescription(description);
    workbench.setLayout(toLayoutValue(layout));
    workbench.setCreatedBy(userId);
    workbench.setUpdatedBy(userId);
    workbench.setCreatedAt(now);
    workbench.setUpdatedAt(now);
    return workbenchRepository.save(workbench);
  }

  private AppWorkbench requireWorkbench(String appId, String workbenchId) {
    Project project = appNavRecordStore.requireAppProject(appId);
    AppWorkbench workbench = workbenchRepository.findByProjectIdAndId(project.getId(), workbenchId);
    if (workbench == null) {
      throw new NotFoundException("工作台不存在: " + workbenchId);
    }
    return workbench;
  }

  private String validatedName(String name) {
    if (isBlank(name)) {
      throw new ValidationException("工作台名称不能为空");
    }
    String normalized = name.trim();
    if (normalized.length() > 100) {
      throw new ValidationException("工作台名称长度不能超过100个字符");
    }
    return normalized;
  }

  private void validateDescription(String description) {
    if (description != null && description.length() > 500) {
      throw new ValidationException("工作台描述长度不能超过500个字符");
    }
  }

  private void requireUniqueName(String projectId, String name, String ignoredWorkbenchId) {
    AppWorkbench existing = workbenchRepository.findByName(projectId, name);
    if (existing != null && !Objects.equals(existing.getId(), ignoredWorkbenchId)) {
      throw new ValidationException("工作台名称已存在: " + name);
    }
  }

  private WorkbenchLayoutRecord validatedLayout(String appId, WorkbenchLayoutRecord layout) {
    if (layout == null) {
      throw new ValidationException("工作台布局不能为空");
    }
    if (!Objects.equals(layout.schemaVersion(), WorkbenchLayoutRecord.CURRENT_SCHEMA_VERSION)) {
      throw new ValidationException("工作台布局版本不合法: " + layout.schemaVersion());
    }
    List<WorkbenchComponentRecord> components = layout.components();
    if (components == null) {
      throw new ValidationException("工作台组件不能为空");
    }

    Set<String> componentIds = new HashSet<>();
    for (WorkbenchComponentRecord component : components) {
      if (component == null || isBlank(component.id())) {
        throw new ValidationException("工作台组件ID不能为空");
      }
      if (!componentIds.add(component.id())) {
        throw new ValidationException("工作台组件ID重复: " + component.id());
      }
      if (isBlank(component.title())) {
        throw new ValidationException("工作台组件标题不能为空");
      }
      if (component.title().trim().length() > 100) {
        throw new ValidationException("工作台组件标题长度不能超过100个字符");
      }
      validatePlacement(component.placement(), component.id());
      validateComponentConfig(appId, component);
    }
    validateNoLayoutOverlap(components);
    return new WorkbenchLayoutRecord(layout.schemaVersion(), components);
  }

  private void validatePlacement(WorkbenchPlacementRecord placement, String componentId) {
    if (placement == null) {
      throw new ValidationException("工作台组件必须配置位置: " + componentId);
    }
    if (placement.x() == null || placement.x() < 0) {
      throw new ValidationException("工作台布局横向位置不合法: " + componentId);
    }
    if (placement.y() == null || placement.y() < 0) {
      throw new ValidationException("工作台布局纵向位置不合法: " + componentId);
    }
    if (placement.w() == null || placement.w() < 1 || placement.w() > GRID_COLUMNS) {
      throw new ValidationException("工作台布局宽度不合法: " + componentId);
    }
    if (placement.h() == null || placement.h() < 1 || placement.h() > MAX_GRID_HEIGHT) {
      throw new ValidationException("工作台布局高度不合法: " + componentId);
    }
    if (placement.x() + placement.w() > GRID_COLUMNS) {
      throw new ValidationException("工作台布局超出网格宽度: " + componentId);
    }
    if (placement.y() + placement.h() > MAX_GRID_HEIGHT) {
      throw new ValidationException("工作台布局超出网格高度: " + componentId);
    }
  }

  private void validateNoLayoutOverlap(List<WorkbenchComponentRecord> components) {
    boolean[] occupied = new boolean[GRID_COLUMNS * MAX_GRID_HEIGHT];
    for (WorkbenchComponentRecord component : components) {
      WorkbenchPlacementRecord placement = component.placement();
      markOccupiedArea(occupied, placement.x(), placement.y(), placement.w(), placement.h());
    }
  }

  private void markOccupiedArea(boolean[] occupied, int x, int y, int width, int height) {
    for (int row = y; row < y + height; row++) {
      for (int column = x; column < x + width; column++) {
        int index = row * GRID_COLUMNS + column;
        if (index < 0 || index >= occupied.length || occupied[index]) {
          throw new ValidationException("工作台布局项重叠: " + (row + 1) + "行" + (column + 1) + "列");
        }
        occupied[index] = true;
      }
    }
  }

  private void validateComponentConfig(String appId, WorkbenchComponentRecord component) {
    WorkbenchComponentConfig config = component.config();
    if (config == null) {
      throw new ValidationException("工作台组件配置不能为空: " + component.id());
    }
    if (config instanceof WorkbenchTasksConfig) {
      return;
    }
    if (config instanceof WorkbenchMetricConfig metricConfig) {
      requireForm(appId, metricConfig.formName());
      if (isBlank(metricConfig.aggregation()) || !AGGREGATIONS.contains(metricConfig.aggregation())) {
        throw new ValidationException("工作台指标聚合方式不合法: " + metricConfig.aggregation());
      }
      if ("count".equals(metricConfig.aggregation())) {
        if (!isBlank(metricConfig.field())) {
          throw new ValidationException("工作台记录数指标不能配置聚合字段: " + metricConfig.field());
        }
      } else {
        AppFormFieldResponse field = requireField(requireForm(appId, metricConfig.formName()), metricConfig.field());
        if (field == null) {
          throw new ValidationException("工作台指标聚合字段不存在: " + metricConfig.field());
        }
        if (!MEASURE_FIELD_TYPES.contains(field.type())) {
          throw new ValidationException("工作台指标聚合字段必须是数字字段: " + metricConfig.field());
        }
      }
      return;
    }
    if (config instanceof WorkbenchChartConfig chartConfig) {
      if (isBlank(chartConfig.chartType()) || !CHART_TYPES.contains(chartConfig.chartType())) {
        throw new ValidationException("工作台图表类型不合法: " + chartConfig.chartType());
      }
      AppFormResponse form = requireForm(appId, chartConfig.formName());
      if (isBlank(chartConfig.dimensionField())) {
        throw new ValidationException("工作台图表必须配置分组字段: " + component.title());
      }
      AppFormFieldResponse dimensionField = requireField(form, chartConfig.dimensionField());
      if (dimensionField == null) {
        throw new ValidationException("工作台分组字段不存在: " + chartConfig.dimensionField());
      }
      if (!GROUPABLE_FIELD_TYPES.contains(dimensionField.type())) {
        throw new ValidationException("工作台分组字段类型不支持: " + dimensionField.type());
      }
      if (isBlank(chartConfig.aggregation()) || !AGGREGATIONS.contains(chartConfig.aggregation())) {
        throw new ValidationException("工作台聚合方式不合法: " + chartConfig.aggregation());
      }
      if ("count".equals(chartConfig.aggregation())) {
        if (!isBlank(chartConfig.measureField())) {
          throw new ValidationException("工作台记录数图表不能配置指标字段: " + chartConfig.measureField());
        }
      } else {
        AppFormFieldResponse field = requireField(form, chartConfig.measureField());
        if (field == null) {
          throw new ValidationException("工作台指标字段不存在: " + chartConfig.measureField());
        }
        if (!MEASURE_FIELD_TYPES.contains(field.type())) {
          throw new ValidationException("工作台指标字段必须是数字字段: " + chartConfig.measureField());
        }
      }
      if (chartConfig.showLegend() == null || chartConfig.showLabel() == null
        || ("line".equals(chartConfig.chartType()) && chartConfig.smooth() == null)) {
        throw new ValidationException("工作台图表显示配置不能为空: " + component.title());
      }
      if (isBlank(chartConfig.palette()) || !PALETTES.contains(chartConfig.palette())) {
        throw new ValidationException("工作台配色不合法: " + chartConfig.palette());
      }
      return;
    }
    if (config instanceof WorkbenchDataLoaderConfig dataLoaderConfig) {
      AppFormResponse form = requireForm(appId, dataLoaderConfig.formName());
      validateVisibleColumns(form, dataLoaderConfig.visibleColumns(), component.title());
      return;
    }
    if (config instanceof WorkbenchShortcutsConfig shortcutsConfig) {
      if (shortcutsConfig.columnsPerRow() == null
        || shortcutsConfig.columnsPerRow() < 1
        || shortcutsConfig.columnsPerRow() > 12) {
        throw new ValidationException("工作台快捷入口每行数量必须在 1 到 12 之间: " + component.title());
      }
      Project project = appNavRecordStore.requireAppProject(appId);
      List<AppNavRecord> navs = appNavRecordStore.navRecords(project);
      List<String> shortcuts = shortcutsConfig.shortcuts() == null ? List.of() : shortcutsConfig.shortcuts();
      Set<String> shortcutIds = new HashSet<>();
      for (String navId : shortcuts) {
        if (isBlank(navId) || !shortcutIds.add(navId)) {
          throw new ValidationException("工作台快捷入口菜单不合法");
        }
        AppNavRecord nav = findNav(navs, navId);
        if (nav == null || nav.destination() == null || !"FORM".equals(nav.destination().type())) {
          throw new ValidationException("工作台快捷入口必须关联表单菜单: " + navId);
        }
      }
      return;
    }
    throw new ValidationException("工作台组件配置类型不合法");
  }

  private void validateVisibleColumns(
    AppFormResponse form,
    List<String> visibleColumns,
    String title
  ) {
    if (visibleColumns == null || visibleColumns.isEmpty()) {
      throw new ValidationException("工作台明细组件必须配置显示列: " + title);
    }
    Set<String> names = new HashSet<>();
    for (String columnName : visibleColumns) {
      if (isBlank(columnName) || !names.add(columnName) || requireField(form, columnName) == null) {
        throw new ValidationException("工作台明细列不合法: " + columnName);
      }
    }
  }

  private AppFormResponse requireForm(String appId, String formName) {
    if (isBlank(formName)) {
      throw new ValidationException("工作台组件必须配置表单");
    }
    return appFormService.findForms(appId).stream()
      .filter(form -> Objects.equals(form.name(), formName))
      .findFirst()
      .orElseThrow(() -> new ValidationException("工作台组件表单不存在: " + formName));
  }

  private AppFormFieldResponse requireField(AppFormResponse form, String fieldName) {
    if (isBlank(fieldName)) {
      return null;
    }
    return form.fields().stream()
      .filter(field -> Objects.equals(field.name(), fieldName))
      .findFirst()
      .orElse(null);
  }

  private AppNavRecord findNav(List<AppNavRecord> navs, String navId) {
    for (AppNavRecord nav : navs) {
      if (Objects.equals(nav.id(), navId)) {
        return nav;
      }
      AppNavRecord child = nav.children() == null ? null : findNav(nav.children(), navId);
      if (child != null) {
        return child;
      }
    }
    return null;
  }

  private List<WorkbenchComponentSpecRecord> componentSpecs() {
    return List.of(
      new WorkbenchComponentSpecRecord("metric", null, "指标", "突出展示核心指标", 3, 3,
        List.of("type", "formName", "aggregation"), Map.of(
        "type", "metric", "formName", "<form.name>", "aggregation", "count"),
        DATA_ENDPOINT_TEMPLATE),
      new WorkbenchComponentSpecRecord("chart", "line", "折线图", "观察数据趋势", 6, 4,
        List.of("type", "chartType", "formName", "dimensionField",
          "aggregation", "showLegend", "showLabel", "smooth", "palette"), Map.of(
        "type", "chart", "chartType", "line", "formName", "<form.name>",
        "dimensionField", "<field.name>",
        "aggregation", "count", "showLegend", false, "showLabel", true,
        "smooth", true, "palette", "primary"),
        DATA_ENDPOINT_TEMPLATE),
      new WorkbenchComponentSpecRecord("chart", "bar", "柱状图", "比较分组数据", 6, 4,
        List.of("type", "chartType", "formName", "dimensionField",
          "aggregation", "showLegend", "showLabel", "palette"), Map.of(
        "type", "chart", "chartType", "bar", "formName", "<form.name>",
        "dimensionField", "<field.name>",
        "aggregation", "count", "showLegend", false, "showLabel", true,
        "palette", "primary"),
        DATA_ENDPOINT_TEMPLATE),
      new WorkbenchComponentSpecRecord("chart", "pie", "饼图", "展示分组占比", 4, 4,
        List.of("type", "chartType", "formName", "dimensionField",
          "aggregation", "showLegend", "showLabel", "palette"), Map.of(
        "type", "chart", "chartType", "pie", "formName", "<form.name>",
        "dimensionField", "<field.name>",
        "aggregation", "count", "showLegend", true, "showLabel", true,
        "palette", "multi"),
        DATA_ENDPOINT_TEMPLATE),
      new WorkbenchComponentSpecRecord("dataloader", null, "明细", "查看表单记录", 12, 5,
        List.of("type", "formName", "visibleColumns"), Map.of(
        "type", "dataloader", "formName", "<form.name>", "visibleColumns", List.of("<field.name>")),
        DATA_ENDPOINT_TEMPLATE),
      new WorkbenchComponentSpecRecord("shortcuts", null, "快捷入口", "直达表单菜单", 6, 3,
        List.of("type", "shortcuts", "columnsPerRow"), Map.of(
        "type", "shortcuts", "shortcuts", List.of("<nav.id>"), "columnsPerRow", 2),
        null),
      new WorkbenchComponentSpecRecord("tasks", null, "我的任务", "查看流程待办", 4, 4,
        List.of("type"), Map.of("type", "tasks"), null));
  }

  private WorkbenchFormOptionRecord toFormOption(AppFormResponse form) {
    return new WorkbenchFormOptionRecord(
      form.name(),
      form.label(),
      form.displayField(),
      form.fields().stream()
        .map(field -> new WorkbenchFieldOptionRecord(
          field.name(), field.label(), field.type(), MEASURE_FIELD_TYPES.contains(field.type())))
        .toList());
  }

  private List<WorkbenchNavOptionRecord> shortcutNavOptions(List<AppNavRecord> navs) {
    List<WorkbenchNavOptionRecord> result = new ArrayList<>();
    for (AppNavRecord nav : navs) {
      if (Objects.equals(nav.type(), "ENTRY")
        && nav.destination() != null
        && Objects.equals(nav.destination().type(), "FORM")) {
        result.add(new WorkbenchNavOptionRecord(nav.id(), nav.name()));
      }
      if (nav.children() != null) {
        result.addAll(shortcutNavOptions(nav.children()));
      }
    }
    return result;
  }

  private Map<String, List<String>> availableValues() {
    Map<String, List<String>> result = new LinkedHashMap<>();
    result.put("chartType", List.of("line", "bar", "pie"));
    result.put("aggregation", List.of("count", "sum", "avg"));
    result.put("palette", List.of("primary", "multi"));
    result.put("columnsPerRow", List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12"));
    return result;
  }

  private WorkbenchLayoutRecord exampleLayout() {
    WorkbenchComponentRecord component = new WorkbenchComponentRecord(
      "component_tasks",
      "我的任务",
      new WorkbenchPlacementRecord(0, 0, 4, 4),
      new WorkbenchTasksConfig());
    return new WorkbenchLayoutRecord(WorkbenchLayoutRecord.CURRENT_SCHEMA_VERSION, List.of(component));
  }

  private Object toLayoutValue(WorkbenchLayoutRecord layout) {
    return JsonUtils.convertValue(layout, Map.class);
  }

  private WorkbenchLayoutRecord toLayout(AppWorkbench workbench) {
    if (workbench.getLayout() == null) {
      return WorkbenchLayoutRecord.empty();
    }
    return JsonUtils.convertValue(workbench.getLayout(), WorkbenchLayoutRecord.class);
  }

  private WorkbenchSummaryResponse toSummary(AppWorkbench workbench) {
    return new WorkbenchSummaryResponse(
      workbench.getId(),
      workbench.getName(),
      workbench.getDescription(),
      workbench.getCreatedAt(),
      workbench.getUpdatedAt());
  }

  private WorkbenchResponse toResponse(AppWorkbench workbench) {
    return new WorkbenchResponse(
      workbench.getId(),
      workbench.getName(),
      workbench.getDescription(),
      toLayout(workbench),
      workbench.getCreatedAt(),
      workbench.getUpdatedAt());
  }

  private String currentUserId() {
    String userId = sessionContext.getUserId();
    return isBlank(userId) ? "admin" : userId;
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
