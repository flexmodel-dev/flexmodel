package dev.flexmodel.apps;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.apps.dto.AppFormResponse;
import dev.flexmodel.apps.dto.AppMenuOrderRequest;
import dev.flexmodel.apps.dto.AppMenuRecord;
import dev.flexmodel.apps.dto.AppMenuResponse;
import dev.flexmodel.apps.dto.AppMenuSaveRequest;
import dev.flexmodel.apps.dto.AppMenuTargetRecord;
import dev.flexmodel.apps.dto.AppMenuTargetRequest;
import dev.flexmodel.apps.dto.AppMenuTargetResponse;
import dev.flexmodel.apps.dto.AppMenuTargetsResponse;
import dev.flexmodel.codegen.entity.Project;
import dev.flexmodel.common.NotFoundException;
import dev.flexmodel.common.SessionContext;
import dev.flexmodel.common.ValidationException;
import dev.flexmodel.project.ProjectService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static java.util.stream.Collectors.toMap;

/**
 * 应用菜单服务，菜单存储在项目 metadata 的 app.menus 节点。
 *
 * @author cjbi
 */
@ApplicationScoped
public class AppMenuService {

  private static final String GROUP_TYPE = "GROUP";
  private static final String ITEM_TYPE = "ITEM";
  private static final String BUILTIN_TARGET_TYPE = "BUILTIN";
  private static final String FORM_TARGET_TYPE = "FORM";
  private static final String METADATA_APP_KEY = "app";
  private static final Set<String> MENU_ICON_NAMES = Set.of(
    "Home", "User", "TrendingUp", "ShoppingCart", "PieChart", "Folder", "BookUser",
    "ClipboardCheck", "Shapes", "Tags", "Boxes", "Banknote");
  private static final Map<String, String> BUILTIN_TARGETS = builtInTargets();

  @Inject
  ProjectService projectService;

  @Inject
  AppFormService appFormService;

  @Inject
  SessionContext sessionContext;

  public List<AppMenuResponse> findMenus(String appId) {
    Project project = requireAppProject(appId);
    return toResponses(appId, menuRecords(project), formLabels(appId));
  }

  public AppMenuTargetsResponse findTargets(String appId) {
    requireAppProject(appId);
    return new AppMenuTargetsResponse(builtInResponses(), formResponses(appId));
  }

  public AppMenuResponse createMenu(String appId, AppMenuSaveRequest request) {
    Project project = requireAppProject(appId);
    Map<String, String> formLabels = formLabels(appId);
    List<AppMenuRecord> menus = menuRecords(project);
    AppMenuRecord menu = validatedMenu(request, appId, null);

    if (findNode(menus, menu.id()) != null) {
      throw new ValidationException("菜单ID已存在: " + menu.id());
    }
    AppMenuRecord parent = requireParent(menus, menu.parentId());
    requireUniqueName(menus, menu, null);

    List<AppMenuRecord> nextMenus = new ArrayList<>(menus);
    if (parent == null) {
      nextMenus.add(menu);
    } else {
      nextMenus = replaceNode(nextMenus, withChildren(parent, appended(parent.children(), menu)));
    }
    saveMenus(appId, project, nextMenus, formLabels);
    return toResponse(appId, menu, formLabels);
  }

  public AppMenuResponse updateMenu(String appId, String menuId, AppMenuSaveRequest request) {
    Project project = requireAppProject(appId);
    Map<String, String> formLabels = formLabels(appId);
    List<AppMenuRecord> menus = menuRecords(project);
    AppMenuRecord existing = requireMenu(menus, menuId);
    if (!Objects.equals(existing.type(), request.type())) {
      throw new ValidationException("菜单类型不能修改");
    }

    AppMenuRecord menu = validatedMenu(request, appId, existing);
    AppMenuRecord requestedParent = requireParent(menus, menu.parentId());
    if (requestedParent != null && requestedParent.id().equals(menu.id())) {
      throw new ValidationException("父级菜单不能是自身");
    }
    requireUniqueName(menus, menu, menu.id());

    List<AppMenuRecord> nextMenus = removeNode(menus, menu.id());
    AppMenuRecord parent = menu.parentId() == null ? null : requireParent(nextMenus, menu.parentId());
    if (parent == null) {
      nextMenus.add(menu);
    } else {
      nextMenus = replaceNode(nextMenus, withChildren(parent, appended(parent.children(), menu)));
    }
    saveMenus(appId, project, nextMenus, formLabels);
    return toResponse(appId, menu, formLabels);
  }

  public void deleteMenu(String appId, String menuId) {
    Project project = requireAppProject(appId);
    Map<String, String> formLabels = formLabels(appId);
    List<AppMenuRecord> menus = menuRecords(project);
    requireMenu(menus, menuId);
    saveMenus(appId, project, removeNode(menus, menuId), formLabels);
  }

  public List<AppMenuResponse> reorderMenus(String appId, AppMenuOrderRequest request) {
    Project project = requireAppProject(appId);
    Map<String, String> formLabels = formLabels(appId);
    List<AppMenuRecord> menus = menuRecords(project);
    List<AppMenuRecord> nextMenus = orderedMenus(menus, request);
    return saveMenus(appId, project, nextMenus, formLabels);
  }

  private AppMenuRecord validatedMenu(AppMenuSaveRequest request, String appId, AppMenuRecord existing) {
    String type = request.type();
    if (!GROUP_TYPE.equals(type) && !ITEM_TYPE.equals(type)) {
      throw new ValidationException("菜单类型不合法: " + type);
    }
    if (isBlank(request.name())) {
      throw new ValidationException("菜单名称不能为空");
    }
    String name = request.name().trim();
    if (name.length() > 50) {
      throw new ValidationException("菜单名称长度不能超过50个字符");
    }
    if (request.iconName() != null && !MENU_ICON_NAMES.contains(request.iconName())) {
      throw new ValidationException("菜单图标不合法: " + request.iconName());
    }

    String parentId = isBlank(request.parentId()) ? null : request.parentId();
    AppMenuTargetRecord target = null;
    if (ITEM_TYPE.equals(type)) {
      AppMenuTargetRequest targetRequest = request.target();
      if (targetRequest == null || isBlank(targetRequest.type()) || isBlank(targetRequest.id())) {
        throw new ValidationException("菜单必须选择关联项后才能绑定页面");
      }
      if (!BUILTIN_TARGET_TYPE.equals(targetRequest.type())
        && !FORM_TARGET_TYPE.equals(targetRequest.type())) {
        throw new ValidationException("菜单关联类型不合法: " + targetRequest.type());
      }
      if (BUILTIN_TARGET_TYPE.equals(targetRequest.type()) && !BUILTIN_TARGETS.containsKey(targetRequest.id())) {
        throw new ValidationException("内置模块不存在: " + targetRequest.id());
      }
      if (BUILTIN_TARGET_TYPE.equals(targetRequest.type())) {
        target = new AppMenuTargetRecord(BUILTIN_TARGET_TYPE, targetRequest.id());
      }
      if (FORM_TARGET_TYPE.equals(targetRequest.type())) {
        requireFormName(appId, targetRequest.id());
        target = new AppMenuTargetRecord(FORM_TARGET_TYPE, targetRequest.id());
      }
    } else if (request.target() != null) {
      throw new ValidationException("分组不能绑定关联项");
    }

    String id = existing == null ? generateMenuId() : existing.id();
    List<AppMenuRecord> children = existing == null
      ? (GROUP_TYPE.equals(type) ? List.of() : List.of())
      : existing.children();
    return new AppMenuRecord(id, type, name, request.iconName(), parentId, target, children);
  }

  private List<AppMenuRecord> orderedMenus(List<AppMenuRecord> menus, AppMenuOrderRequest request) {
    Map<String, AppMenuRecord> nodesById = new LinkedHashMap<>();
    menus.forEach(menu -> collectNodes(menu, nodesById));
    List<String> rootIds = request.root();
    Map<String, List<String>> childrenOrder = request.children() == null ? Map.of() : request.children();

    Set<String> expectedRootIds = Set.copyOf(menus.stream().map(AppMenuRecord::id).toList());
    if (rootIds.size() != expectedRootIds.size() || !expectedRootIds.containsAll(rootIds)) {
      throw new ValidationException("菜单排序必须包含且仅包含所有根级菜单");
    }
    if (rootIds.stream().distinct().count() != rootIds.size()) {
      throw new ValidationException("根级菜单存在重复排序");
    }

    Map<String, List<String>> expectedChildren = new LinkedHashMap<>();
    collectExpectedChildren(menus, expectedChildren);
    if (!childrenOrder.keySet().equals(expectedChildren.keySet())) {
      throw new ValidationException("菜单排序必须包含且仅包含所有分组");
    }
    childrenOrder.forEach((groupId, orderedChildIds) -> {
      List<String> expected = expectedChildren.get(groupId);
      if (orderedChildIds == null
        || orderedChildIds.size() != expected.size()
        || !expected.containsAll(orderedChildIds)
        || orderedChildIds.stream().distinct().count() != orderedChildIds.size()) {
        throw new ValidationException("分组「" + nodesById.get(groupId).name() + "」的子菜单排序不完整");
      }
    });

    List<AppMenuRecord> result = new ArrayList<>();
    for (String rootId : rootIds) {
      AppMenuRecord menu = nodesById.get(rootId);
      if (GROUP_TYPE.equals(menu.type())) {
        result.add(withChildren(menu, orderedChildren(menu.id(), childrenOrder, nodesById)));
      } else {
        result.add(menu);
      }
    }
    return result;
  }

  private void collectExpectedChildren(
    List<AppMenuRecord> menus,
    Map<String, List<String>> expectedChildren
  ) {
    for (AppMenuRecord menu : menus) {
      if (GROUP_TYPE.equals(menu.type())) {
        expectedChildren.put(menu.id(), menu.children() == null
          ? List.of()
          : menu.children().stream().map(AppMenuRecord::id).toList());
        if (menu.children() != null) collectExpectedChildren(menu.children(), expectedChildren);
      }
    }
  }

  private List<AppMenuRecord> orderedChildren(
    String parentId,
    Map<String, List<String>> childrenOrder,
    Map<String, AppMenuRecord> nodesById
  ) {
    List<String> orderedIds = childrenOrder.getOrDefault(parentId, List.of());
    return orderedIds.stream()
      .map(nodesById::get)
      .map(child -> GROUP_TYPE.equals(child.type())
        ? withChildren(child, orderedChildren(child.id(), childrenOrder, nodesById))
        : child)
      .toList();
  }

  private List<AppMenuResponse> saveMenus(
    String appId,
    Project project,
    List<AppMenuRecord> menus,
    Map<String, String> formLabels
  ) {
    Map<String, Object> metadata = metadataMap(project);
    Map<String, Object> app = appNode(project);
    app.put("menus", menus);
    metadata.put(METADATA_APP_KEY, app);
    project.setMetadata(metadata);
    project.setUpdatedBy(currentUserId());
    project.setUpdatedAt(LocalDateTime.now());
    Project updated = projectService.updateProject(project);
    return toResponses(appId, menuRecords(updated), formLabels);
  }

  private List<AppMenuRecord> menuRecords(Project project) {
    Object value = appNode(project).get("menus");
    if (value == null) {
      return new ArrayList<>();
    }
    if (!(value instanceof Collection<?> collection)) {
      throw new ValidationException("应用菜单数据格式不正确");
    }
    List<AppMenuRecord> result = new ArrayList<>();
    for (Object item : collection) {
      AppMenuRecord menu = JsonUtils.convertValue(item, AppMenuRecord.class);
      if (menu == null || isBlank(menu.id()) || isBlank(menu.type()) || isBlank(menu.name())) {
        throw new ValidationException("应用菜单数据格式不正确");
      }
      result.add(menu);
    }
    return result;
  }

  private List<AppMenuResponse> toResponses(
    String appId,
    List<AppMenuRecord> menus,
    Map<String, String> formLabels) {
    return menus.stream()
      .map(menu -> toResponse(appId, menu, formLabels))
      .toList();
  }

  private AppMenuResponse toResponse(String appId, AppMenuRecord menu, Map<String, String> formLabels) {
    AppMenuTargetRecord target = menu.target();
    AppMenuTargetResponse targetResponse = target == null ? null : new AppMenuTargetResponse(
      BUILTIN_TARGET_TYPE.equals(target.type()) ? BUILTIN_TARGET_TYPE : FORM_TARGET_TYPE,
      target.id(),
      BUILTIN_TARGET_TYPE.equals(target.type())
        ? BUILTIN_TARGETS.get(target.id())
        : formLabels.getOrDefault(target.id(), target.id()));
    return new AppMenuResponse(
      menu.id(),
      menu.type(),
      menu.name(),
      menu.iconName(),
      menu.parentId(),
      targetResponse,
      ITEM_TYPE.equals(menu.type()) ? "/apps/" + appId + "/" + menu.id() : null,
      menu.children() == null
        ? List.of()
        : menu.children().stream().map(child -> toResponse(appId, child, formLabels)).toList());
  }

  private AppMenuRecord requireMenu(List<AppMenuRecord> menus, String menuId) {
    AppMenuRecord menu = findNode(menus, menuId);
    if (menu == null) {
      throw new NotFoundException("菜单不存在: " + menuId);
    }
    return menu;
  }

  private AppMenuRecord requireParent(List<AppMenuRecord> menus, String parentId) {
    if (parentId == null) {
      return null;
    }
    AppMenuRecord parent = findNode(menus, parentId);
    if (parent == null) {
      throw new ValidationException("父级菜单不存在: " + parentId);
    }
    if (!GROUP_TYPE.equals(parent.type())) {
      throw new ValidationException("父级菜单必须是分组");
    }
    return parent;
  }

  private void requireUniqueName(List<AppMenuRecord> menus, AppMenuRecord menu, String ignoredMenuId) {
    boolean duplicated = flatten(menus).stream()
      .filter(item -> !item.id().equals(ignoredMenuId))
      .anyMatch(item -> Objects.equals(normalizeParent(item.parentId()), normalizeParent(menu.parentId()))
        && item.name().equals(menu.name()));
    if (duplicated) {
      throw new ValidationException("同级菜单名称已存在: " + menu.name());
    }
  }

  private AppMenuRecord findNode(List<AppMenuRecord> menus, String menuId) {
    for (AppMenuRecord menu : menus) {
      if (menu.id().equals(menuId)) {
        return menu;
      }
      AppMenuRecord child = menu.children() == null ? null : findNode(menu.children(), menuId);
      if (child != null) {
        return child;
      }
    }
    return null;
  }

  private List<AppMenuRecord> removeNode(List<AppMenuRecord> menus, String menuId) {
    List<AppMenuRecord> result = new ArrayList<>();
    for (AppMenuRecord menu : menus) {
      if (menu.id().equals(menuId)) {
        continue;
      }
      if (menu.children() == null || menu.children().isEmpty()) {
        result.add(menu);
      } else {
        result.add(withChildren(menu, removeNode(menu.children(), menuId)));
      }
    }
    return result;
  }

  private List<AppMenuRecord> replaceNode(List<AppMenuRecord> menus, AppMenuRecord replacement) {
    List<AppMenuRecord> result = new ArrayList<>();
    for (AppMenuRecord menu : menus) {
      if (menu.id().equals(replacement.id())) {
        result.add(replacement);
      } else if (menu.children() == null || menu.children().isEmpty()) {
        result.add(menu);
      } else {
        result.add(withChildren(menu, replaceNode(menu.children(), replacement)));
      }
    }
    return result;
  }

  private AppMenuRecord withChildren(AppMenuRecord menu, List<AppMenuRecord> children) {
    return new AppMenuRecord(
      menu.id(), menu.type(), menu.name(), menu.iconName(), menu.parentId(), menu.target(), children);
  }

  private List<AppMenuRecord> appended(List<AppMenuRecord> children, AppMenuRecord child) {
    List<AppMenuRecord> result = children == null ? new ArrayList<>() : new ArrayList<>(children);
    result.add(child);
    return result;
  }

  private List<AppMenuRecord> flatten(List<AppMenuRecord> menus) {
    List<AppMenuRecord> result = new ArrayList<>();
    for (AppMenuRecord menu : menus) {
      result.add(menu);
      if (menu.children() != null) {
        result.addAll(flatten(menu.children()));
      }
    }
    return result;
  }

  private void collectNodes(AppMenuRecord menu, Map<String, AppMenuRecord> nodesById) {
    nodesById.put(menu.id(), menu);
    if (menu.children() != null) {
      menu.children().forEach(child -> collectNodes(child, nodesById));
    }
  }

  private Map<String, String> formLabels(String appId) {
    return appFormService.findForms(appId).stream()
      .collect(toMap(
        AppFormResponse::name,
        AppFormResponse::label,
        (left, right) -> right,
        LinkedHashMap::new));
  }

  private void requireFormName(String appId, String formName) {
    appFormService.findForms(appId).stream()
      .filter(form -> form.name().equals(formName))
      .findFirst()
      .orElseThrow(() -> new ValidationException("表单不存在: " + formName));
  }

  private List<AppMenuTargetResponse> builtInResponses() {
    return BUILTIN_TARGETS.entrySet().stream()
      .map(entry -> new AppMenuTargetResponse(BUILTIN_TARGET_TYPE, entry.getKey(), entry.getValue()))
      .toList();
  }

  private List<AppMenuTargetResponse> formResponses(String appId) {
    return appFormService.findForms(appId).stream()
      .map(form -> new AppMenuTargetResponse(FORM_TARGET_TYPE, form.name(), form.label()))
      .toList();
  }

  private Project requireAppProject(String appId) {
    Project project = projectService.findProject(appId);
    if (project == null || project.getParentProjectId() != null || "default".equals(appId)) {
      throw new NotFoundException("应用不存在: " + appId);
    }
    return project;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> metadataMap(Project project) {
    Object metadata = project.getMetadata();
    if (metadata == null) {
      return new LinkedHashMap<>();
    }
    if (metadata instanceof Map<?, ?> map) {
      Map<String, Object> result = new LinkedHashMap<>();
      map.forEach((key, value) -> result.put(String.valueOf(key), value));
      return result;
    }
    return JsonUtils.convertValue(metadata, Map.class);
  }

  private Map<String, Object> appNode(Project project) {
    Object app = metadataMap(project).get(METADATA_APP_KEY);
    if (app == null) {
      return new LinkedHashMap<>();
    }
    if (app instanceof Map<?, ?> map) {
      Map<String, Object> result = new LinkedHashMap<>();
      map.forEach((key, value) -> result.put(String.valueOf(key), value));
      return result;
    }
    return JsonUtils.convertValue(app, Map.class);
  }

  private String normalizeParent(String parentId) {
    return parentId == null ? "" : parentId;
  }

  private String generateMenuId() {
    return "menu_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
  }

  private String currentUserId() {
    String userId = sessionContext.getUserId();
    return isBlank(userId) ? "admin" : userId;
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private static Map<String, String> builtInTargets() {
    Map<String, String> result = new LinkedHashMap<>();
    result.put("dashboard", "工作台");
    result.put("files", "文件");
    result.put("approval", "审批中心");
    return result;
  }
}
