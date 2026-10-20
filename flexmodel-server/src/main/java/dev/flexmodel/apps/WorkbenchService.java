package dev.flexmodel.apps;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.apps.dto.AppFormResponse;
import dev.flexmodel.apps.dto.AppFormFieldResponse;
import dev.flexmodel.apps.dto.AppMenuRecord;
import dev.flexmodel.apps.dto.WorkbenchComponentConfig;
import dev.flexmodel.apps.dto.WorkbenchComponentRecord;
import dev.flexmodel.apps.dto.WorkbenchCreateRequest;
import dev.flexmodel.apps.dto.WorkbenchLayoutItemRecord;
import dev.flexmodel.apps.dto.WorkbenchLayoutRecord;
import dev.flexmodel.apps.dto.WorkbenchPatchRequest;
import dev.flexmodel.apps.dto.WorkbenchResponse;
import dev.flexmodel.apps.dto.WorkbenchSummaryResponse;
import dev.flexmodel.codegen.entity.Project;
import dev.flexmodel.codegen.entity.Workbench;
import dev.flexmodel.common.NotFoundException;
import dev.flexmodel.common.SessionContext;
import dev.flexmodel.common.ValidationException;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class WorkbenchService {

  private static final String DEFAULT_NAME = "默认工作台";
  private static final Set<String> COMPONENT_TYPES = Set.of(
    "index", "line", "bar", "pie", "dataloader", "shortcuts", "tasks");
  private static final Set<String> DATA_COMPONENT_TYPES = Set.of(
    "index", "line", "bar", "pie", "dataloader");
  private static final Set<String> AGGREGATIONS = Set.of("count", "sum", "avg");
  private static final Set<String> MEASURE_FIELD_TYPES = Set.of("NUMBER", "DECIMAL");
  private static final int GRID_COLUMNS = 12;
  private static final int MAX_GRID_HEIGHT = 24;

  @Inject
  WorkbenchRepository workbenchRepository;

  @Inject
  AppMenuRecordStore appMenuRecordStore;

  @Inject
  AppFormService appFormService;

  @Inject
  SessionContext sessionContext;

  @Priority(Interceptor.Priority.APPLICATION + 100)
  void migrateLegacyWorkbenchLayouts(@Observes StartupEvent startupEvent) {
    for (Workbench workbench : workbenchRepository.findAll()) {
      migrateLegacyWorkbenchLayout(workbench);
    }
  }

  public List<WorkbenchSummaryResponse> findWorkbenches(String appId) {
    Project project = appMenuRecordStore.requireAppProject(appId);
    List<Workbench> workbenches = workbenchRepository.findByProjectId(project.getId());
    if (workbenches.isEmpty()) {
      Workbench created = createEntity(project.getId(), DEFAULT_NAME, null, WorkbenchLayoutRecord.empty());
      workbenches = List.of(created);
    }
    return workbenches.stream().map(this::toSummary).toList();
  }

  public WorkbenchResponse findWorkbench(String appId, String workbenchId) {
    Workbench workbench = requireWorkbench(appId, workbenchId);
    return toResponse(workbench);
  }

  public WorkbenchResponse createWorkbench(String appId, WorkbenchCreateRequest request) {
    Project project = appMenuRecordStore.requireAppProject(appId);
    String name = validatedName(request.name());
    validateDescription(request.description());
    requireUniqueName(project.getId(), name, null);
    WorkbenchLayoutRecord layout = request.layout() == null
      ? WorkbenchLayoutRecord.empty()
      : validatedLayout(appId, request.layout());
    Workbench workbench = createEntity(project.getId(), name, request.description(), layout);
    return toResponse(workbench);
  }

  public WorkbenchResponse patchWorkbench(String appId, String workbenchId, WorkbenchPatchRequest request) {
    Project project = appMenuRecordStore.requireAppProject(appId);
    Workbench workbench = requireWorkbench(project.getId(), workbenchId);

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
    Project project = appMenuRecordStore.requireAppProject(appId);
    Workbench workbench = requireWorkbench(project.getId(), workbenchId);
    if (workbenchRepository.countByProjectId(project.getId()) <= 1) {
      throw new ValidationException("至少保留一个工作台");
    }
    workbenchRepository.deleteByProjectIdAndId(project.getId(), workbenchId);
  }

  private Workbench createEntity(
    String projectId,
    String name,
    String description,
    WorkbenchLayoutRecord layout
  ) {
    LocalDateTime now = LocalDateTime.now();
    String userId = currentUserId();
    Workbench workbench = new Workbench();
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

  private Workbench requireWorkbench(String appId, String workbenchId) {
    Workbench workbench = workbenchRepository.findByProjectIdAndId(appId, workbenchId);
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
    Workbench existing = workbenchRepository.findByName(projectId, name);
    if (existing != null && !Objects.equals(existing.getId(), ignoredWorkbenchId)) {
      throw new ValidationException("工作台名称已存在: " + name);
    }
  }

  private WorkbenchLayoutRecord validatedLayout(String appId, WorkbenchLayoutRecord layout) {
    if (layout == null) {
      throw new ValidationException("工作台布局不能为空");
    }
    List<WorkbenchComponentRecord> components = layout.components() == null
      ? List.of()
      : layout.components();
    List<WorkbenchLayoutItemRecord> items = layout.items() == null
      ? List.of()
      : layout.items();
    if (components.size() != items.size()) {
      throw new ValidationException("工作台组件与布局项数量必须一致");
    }

    Set<String> componentIds = new HashSet<>();
    Map<String, WorkbenchComponentRecord> componentsById = new LinkedHashMap<>();
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
      if (!COMPONENT_TYPES.contains(component.type())) {
        throw new ValidationException("工作台组件类型不合法: " + component.type());
      }
      validateComponentConfig(appId, component);
      componentsById.put(component.id(), component);
    }

    Set<String> itemComponentIds = new HashSet<>();
    for (WorkbenchLayoutItemRecord item : items) {
      if (item == null || isBlank(item.componentId())) {
        throw new ValidationException("工作台布局项必须关联组件");
      }
      if (!componentsById.containsKey(item.componentId())) {
        throw new ValidationException("工作台布局项关联的组件不存在: " + item.componentId());
      }
      if (!itemComponentIds.add(item.componentId())) {
        throw new ValidationException("工作台布局项重复: " + item.componentId());
      }
      if (item.x() == null || item.x() < 0) {
        throw new ValidationException("工作台布局横向位置不合法: " + item.x());
      }
      if (item.y() == null || item.y() < 0) {
        throw new ValidationException("工作台布局纵向位置不合法: " + item.y());
      }
      if (item.w() == null || item.w() < 1 || item.w() > GRID_COLUMNS) {
        throw new ValidationException("工作台布局宽度不合法: " + item.w());
      }
      if (item.h() == null || item.h() < 1 || item.h() > MAX_GRID_HEIGHT) {
        throw new ValidationException("工作台布局高度不合法: " + item.h());
      }
      if (item.x() + item.w() > GRID_COLUMNS) {
        throw new ValidationException("工作台布局超出网格宽度: " + item.componentId());
      }
      if (item.y() + item.h() > MAX_GRID_HEIGHT) {
        throw new ValidationException("工作台布局超出网格高度: " + item.componentId());
      }
    }
    validateNoLayoutOverlap(items);
    return new WorkbenchLayoutRecord(components, items);
  }

  void migrateLegacyWorkbenchLayout(Workbench workbench) {
    if (workbench.getLayout() == null) {
      return;
    }
    Map<String, Object> layout = JsonUtils.convertValue(workbench.getLayout(), Map.class);
    List<Map<String, Object>> items = layout.get("items") == null
      ? List.of()
      : JsonUtils.convertValue(layout.get("items"), List.class);
    if (items.isEmpty() || items.stream().noneMatch(WorkbenchService::isLegacyLayoutItem)) {
      return;
    }

    int cursorX = 0;
    int rowY = 0;
    int rowHeight = 0;
    List<Map<String, Object>> migratedItems = new ArrayList<>();
    for (Map<String, Object> item : items) {
      int width = legacyWidth(item.get("width"));
      int height = legacyHeight(item.get("height"));
      if (cursorX + width > GRID_COLUMNS) {
        cursorX = 0;
        rowY += rowHeight;
        rowHeight = 0;
      }
      Map<String, Object> migratedItem = new HashMap<>();
      migratedItem.put("componentId", item.get("componentId"));
      migratedItem.put("x", cursorX);
      migratedItem.put("y", rowY);
      migratedItem.put("w", width);
      migratedItem.put("h", height);
      migratedItems.add(migratedItem);
      cursorX += width;
      rowHeight = Math.max(rowHeight, height);
    }
    layout.put("items", migratedItems);
    workbench.setLayout(layout);
    workbench.setUpdatedBy("system");
    workbench.setUpdatedAt(LocalDateTime.now());
    workbenchRepository.save(workbench);
  }

  private static boolean isLegacyLayoutItem(Map<String, Object> item) {
    return item != null && !item.containsKey("w") && item.containsKey("width");
  }

  private static int legacyWidth(Object width) {
    return switch (String.valueOf(width)) {
      case "QUARTER" -> 3;
      case "THIRD" -> 4;
      case "TWO_THIRDS" -> 8;
      case "THREE_QUARTERS" -> 9;
      case "FULL" -> 12;
      default -> 6;
    };
  }

  private static int legacyHeight(Object height) {
    return switch (String.valueOf(height)) {
      case "SHORT" -> 3;
      case "TALL" -> 5;
      default -> 4;
    };
  }

  private void validateNoLayoutOverlap(List<WorkbenchLayoutItemRecord> items) {
    boolean[] occupied = new boolean[GRID_COLUMNS * MAX_GRID_HEIGHT];
    for (WorkbenchLayoutItemRecord item : items) {
      markOccupiedArea(occupied, item.x(), item.y(), item.w(), item.h());
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
    if ("tasks".equals(component.type())) {
      return;
    }
    if (config == null) {
      throw new ValidationException("工作台组件配置不能为空: " + component.title());
    }
    if (DATA_COMPONENT_TYPES.contains(component.type())) {
      AppFormResponse form = requireForm(appId, config.formName());
      if (!"index".equals(component.type()) && isBlank(config.dimensionField())) {
        throw new ValidationException("工作台图表必须配置分组字段: " + component.title());
      }
      if (!"index".equals(component.type()) && requireField(form, config.dimensionField()) == null) {
        throw new ValidationException("工作台分组字段不存在: " + config.dimensionField());
      }
      if (!AGGREGATIONS.contains(config.aggregation())) {
        throw new ValidationException("工作台聚合方式不合法: " + config.aggregation());
      }
      if (!"count".equals(config.aggregation())) {
        AppFormFieldResponse field = requireField(form, config.measureField());
        if (field == null) {
          throw new ValidationException("工作台指标字段不存在: " + config.measureField());
        }
        if (!MEASURE_FIELD_TYPES.contains(field.type())) {
          throw new ValidationException("工作台指标字段必须是数字字段: " + config.measureField());
        }
      }
      if ("dataloader".equals(component.type())) {
        validateVisibleColumns(form, config.visibleColumns(), component.title());
      }
      return;
    }
    if ("shortcuts".equals(component.type())) {
      Project project = appMenuRecordStore.requireAppProject(appId);
      List<AppMenuRecord> menus = appMenuRecordStore.menuRecords(project);
      List<String> shortcuts = config.shortcuts() == null ? List.of() : config.shortcuts();
      Set<String> shortcutIds = new HashSet<>();
      for (String menuId : shortcuts) {
        if (isBlank(menuId) || !shortcutIds.add(menuId)) {
          throw new ValidationException("工作台快捷入口菜单不合法");
        }
        AppMenuRecord menu = findMenu(menus, menuId);
        if (menu == null || menu.target() == null || !"FORM".equals(menu.target().type())) {
          throw new ValidationException("工作台快捷入口必须关联表单菜单: " + menuId);
        }
      }
    }
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

  private AppMenuRecord findMenu(List<AppMenuRecord> menus, String menuId) {
    for (AppMenuRecord menu : menus) {
      if (Objects.equals(menu.id(), menuId)) {
        return menu;
      }
      AppMenuRecord child = menu.children() == null ? null : findMenu(menu.children(), menuId);
      if (child != null) {
        return child;
      }
    }
    return null;
  }

  private Object toLayoutValue(WorkbenchLayoutRecord layout) {
    return JsonUtils.convertValue(layout, Map.class);
  }

  private WorkbenchLayoutRecord toLayout(Workbench workbench) {
    if (workbench.getLayout() == null) {
      return WorkbenchLayoutRecord.empty();
    }
    return JsonUtils.convertValue(workbench.getLayout(), WorkbenchLayoutRecord.class);
  }

  private WorkbenchSummaryResponse toSummary(Workbench workbench) {
    return new WorkbenchSummaryResponse(
      workbench.getId(),
      workbench.getName(),
      workbench.getDescription(),
      workbench.getCreatedAt(),
      workbench.getUpdatedAt());
  }

  private WorkbenchResponse toResponse(Workbench workbench) {
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
