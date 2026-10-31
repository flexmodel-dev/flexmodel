package dev.flexmodel.apps.navigation;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.apps.form.AppFormService;
import dev.flexmodel.apps.form.dto.AppFormResponse;
import dev.flexmodel.apps.navigation.dto.*;
import dev.flexmodel.codegen.entity.Project;
import dev.flexmodel.common.NotFoundException;
import dev.flexmodel.common.SessionContext;
import dev.flexmodel.common.ValidationException;
import dev.flexmodel.directory.DirectoryService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.net.URI;
import java.util.*;
import java.util.regex.Pattern;

import static dev.flexmodel.common.Values.*;
import static java.util.stream.Collectors.toMap;

/**
 * 应用导航服务，导航存储在 f_app 表的 navigation 列。
 *
 * @author cjbi
 */
@ApplicationScoped
public class AppNavigationService {

  private static final String GROUP_TYPE = "GROUP";
  private static final String ENTRY_TYPE = "ENTRY";
  private static final String BUILTIN_DESTINATION_TYPE = "BUILTIN";
  private static final String FORM_DESTINATION_TYPE = "FORM";
  private static final String LINK_DESTINATION_TYPE = "LINK";
  private static final Pattern NAV_ICON_NAME_PATTERN = Pattern.compile("^[A-Z][A-Za-z0-9]{0,63}$");
  private static final Map<String, String> BUILTIN_DESTINATIONS = builtInDestinations();

  private record DestinationLabels(Map<String, String> forms, String ownerId) {
  }

  @Inject
  AppNavigationRepository appNavRecordStore;

  @Inject
  AppFormService appFormService;

  @Inject
  SessionContext sessionContext;

  @Inject
  DirectoryService directoryService;

  public List<AppNavResponse> findNavigation(String appId) {
    Project project = appNavRecordStore.requireAppProject(appId);
    List<AppNavRecord> navs = appNavRecordStore.navRecords(project);
    return toResponses(appId, visibleNavs(project, navs), destinationLabels(appId));
  }

  /**
   * 当前用户是否有权访问指定表单：所有者/管理员放行，未绑定导航的表单放行，否则按导航可见性判断。
   */
  public boolean canAccessForm(String appId, String formName) {
    Project project = appNavRecordStore.requireAppProject(appId);
    if (!sessionContext.isAuthenticatedUser()) return false;
    String userId = sessionContext.getUserId();
    if (userId == null || userId.isBlank()) return false;
    String ownerId = Objects.requireNonNullElse(project.getOwnerId(), "admin");
    if (userId.equals(ownerId)) return true;
    Set<String> permissions = sessionContext.getPermissions();
    if (permissions != null && permissions.contains("*")) return true;
    List<AppNavRecord> navs = appNavRecordStore.navRecords(project);
    if (flatten(navs).stream().noneMatch(nav -> formDestination(nav, formName))) return true;
    return flatten(visibleNavs(project, navs)).stream().anyMatch(nav -> formDestination(nav, formName));
  }

  /**
   * 当前用户是否应用所有者或管理员。
   */
  public boolean isOwnerOrAdmin(String appId) {
    Project project = appNavRecordStore.requireAppProject(appId);
    if (!sessionContext.isAuthenticatedUser()) return false;
    String userId = sessionContext.getUserId();
    if (userId == null || userId.isBlank()) return false;
    String ownerId = Objects.requireNonNullElse(project.getOwnerId(), "admin");
    if (userId.equals(ownerId)) return true;
    Set<String> permissions = sessionContext.getPermissions();
    if (permissions != null && permissions.contains("*")) return true;
    return objects(directoryService.directory().get("members")).stream()
      .anyMatch(member -> userId.equals(text(member, "id")) && strings(member.get("roleIds")).contains("admin"));
  }

  private boolean formDestination(AppNavRecord nav, String formName) {
    return ENTRY_TYPE.equals(nav.type()) && nav.destination() != null
      && FORM_DESTINATION_TYPE.equals(nav.destination().type()) && formName.equals(nav.destination().id());
  }

  public NavDestinationsResponse findDestinations(String appId) {
    appNavRecordStore.requireAppProject(appId);
    return new NavDestinationsResponse(
      builtInResponses(),
      formResponses(appId));
  }

  public AppNavResponse createNav(String appId, AppNavSaveRequest request) {
    Project project = appNavRecordStore.requireAppProject(appId);
    DestinationLabels labels = destinationLabels(appId);
    List<AppNavRecord> navs = appNavRecordStore.navRecords(project);
    AppNavRecord nav = validatedNav(request, appId, null);

    if (findNode(navs, nav.id()) != null) {
      throw new ValidationException("菜单ID已存在: " + nav.id());
    }
    AppNavRecord parent = requireParent(navs, nav.parentId());
    requireUniqueName(navs, nav, null);

    List<AppNavRecord> nextNavs = new ArrayList<>(navs);
    if (parent == null) {
      nextNavs.add(nav);
    } else {
      nextNavs = replaceNode(nextNavs, withChildren(parent, appended(parent.children(), nav)));
    }
    saveNavigation(appId, project, nextNavs, labels);
    return toResponse(appId, nav, labels);
  }

  public AppNavResponse updateNav(String appId, String navId, AppNavSaveRequest request) {
    Project project = appNavRecordStore.requireAppProject(appId);
    DestinationLabels labels = destinationLabels(appId);
    List<AppNavRecord> navs = appNavRecordStore.navRecords(project);
    AppNavRecord existing = requireNav(navs, navId);
    if (!Objects.equals(existing.type(), request.type())) {
      throw new ValidationException("菜单类型不能修改");
    }

    AppNavRecord nav = validatedNav(request, appId, existing);
    AppNavRecord requestedParent = requireParent(navs, nav.parentId());
    if (requestedParent != null && requestedParent.id().equals(nav.id())) {
      throw new ValidationException("父级菜单不能是自身");
    }
    requireUniqueName(navs, nav, nav.id());

    if (Objects.equals(normalizeParent(existing.parentId()), normalizeParent(nav.parentId()))) {
      saveNavigation(appId, project, replaceNode(navs, nav), labels);
      return toResponse(appId, nav, labels);
    }

    List<AppNavRecord> nextNavs = removeNode(navs, nav.id());
    AppNavRecord parent = nav.parentId() == null ? null : requireParent(nextNavs, nav.parentId());
    if (parent == null) {
      nextNavs.add(nav);
    } else {
      nextNavs = replaceNode(nextNavs, withChildren(parent, appended(parent.children(), nav)));
    }
    saveNavigation(appId, project, nextNavs, labels);
    return toResponse(appId, nav, labels);
  }

  public void deleteNav(String appId, String navId) {
    Project project = appNavRecordStore.requireAppProject(appId);
    DestinationLabels labels = destinationLabels(appId);
    List<AppNavRecord> navs = appNavRecordStore.navRecords(project);
    requireNav(navs, navId);
    saveNavigation(appId, project, removeNode(navs, navId), labels);
  }

  public AppNavResponse patchNav(String appId, String navId, Map<String, Object> patch) {
    if (patch == null || !Set.of("type", "name", "iconName", "parentId", "destination", "visibility").containsAll(patch.keySet()))
      throw new ValidationException("菜单局部更新包含未知字段");
    if (patch.containsKey("visibility") && !(patch.get("visibility") instanceof List<?>))
      throw new ValidationException("菜单授权成员必须为数组");
    Project project = appNavRecordStore.requireAppProject(appId);
    AppNavRecord nav = requireNav(appNavRecordStore.navRecords(project), navId);
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("type", nav.type());
    values.put("name", nav.name());
    values.put("iconName", nav.iconName());
    values.put("parentId", nav.parentId());
    values.put("destination", nav.destination());
    values.put("visibility", nav.visibility());
    values.putAll(patch);
    AppNavSaveRequest request;
    try {
      request = JsonUtils.convertValue(values, AppNavSaveRequest.class);
    } catch (IllegalArgumentException exception) {
      throw new ValidationException("菜单局部更新格式不正确");
    }
    return updateNav(appId, navId, request);
  }

  public List<AppNavResponse> reorderNavigation(String appId, AppNavOrderRequest request) {
    Project project = appNavRecordStore.requireAppProject(appId);
    DestinationLabels labels = destinationLabels(appId);
    List<AppNavRecord> navs = appNavRecordStore.navRecords(project);
    List<AppNavRecord> nextNavs = orderedNavs(navs, request);
    return saveNavigation(appId, project, nextNavs, labels);
  }

  private List<AppNavRecord> visibleNavs(Project project, List<AppNavRecord> navs) {
    String userId = sessionContext.getUserId();
    if (isBlank(userId)) return List.of();
    String ownerId = Objects.requireNonNullElse(project.getOwnerId(), "admin");
    if (userId.equals(ownerId)) return navs;
    boolean restricted = flatten(navs).stream()
      .anyMatch(nav -> nav.visibility() != null && !nav.visibility().isEmpty());
    if (!restricted) return navs;
    UserAccess access = userAccess(userId);
    return navs.stream()
      .map(nav -> authorizedNav(nav, access))
      .filter(Objects::nonNull)
      .toList();
  }

  private AppNavRecord authorizedNav(AppNavRecord nav, UserAccess access) {
    if (!authorized(nav.visibility(), access)) return null;
    if (!GROUP_TYPE.equals(nav.type())) return nav;
    List<AppNavRecord> children = nav.children() == null ? List.of()
      : nav.children().stream()
      .map(child -> authorizedNav(child, access))
      .filter(Objects::nonNull)
      .toList();
    return children.isEmpty() ? null : withChildren(nav, children);
  }

  private boolean authorized(List<NavVisibilityRule> visibility, UserAccess access) {
    if (visibility == null || visibility.isEmpty()) return true;
    return visibility.stream().anyMatch(rule -> rule != null && access.matches(rule));
  }

  private UserAccess userAccess(String userId) {
    Map<String, Object> directory = directoryService.directory();
    Map<String, Object> current = objects(directory.get("members")).stream()
      .filter(member -> userId.equals(text(member, "id")))
      .findFirst()
      .orElse(null);
    Set<String> roleIds = current == null ? Set.of() : Set.copyOf(strings(current.get("roleIds")));
    Set<String> organizationIds = current == null ? Set.of() : Set.copyOf(strings(current.get("organizationIds")));
    Map<String, String> organizationParents = new HashMap<>();
    objects(directory.get("organizations")).forEach(organization -> {
      String id = text(organization, "id");
      String parentId = text(organization, "parentId");
      if (!id.isBlank()) organizationParents.put(id, parentId.isBlank() ? null : parentId);
    });
    return new UserAccess(userId, roleIds, organizationIds, organizationParents);
  }

  private record UserAccess(
    String userId,
    Set<String> roleIds,
    Set<String> organizationIds,
    Map<String, String> organizationParents
  ) {
    boolean matches(NavVisibilityRule rule) {
      return switch (rule.type()) {
        case "USER" -> userId.equals(rule.id());
        case "ROLE" -> roleIds.contains(rule.id());
        case "ORGANIZATION" -> organizationIds.contains(rule.id())
          || (Boolean.TRUE.equals(rule.includeChildren())
          && organizationIds.stream().anyMatch(id -> belongsTo(id, rule.id())));
        default -> false;
      };
    }

    private boolean belongsTo(String organizationId, String ancestorId) {
      String current = organizationId;
      Set<String> visited = new HashSet<>();
      while (current != null && visited.add(current)) {
        if (current.equals(ancestorId)) return true;
        current = organizationParents.get(current);
      }
      return false;
    }
  }

  private AppNavRecord validatedNav(AppNavSaveRequest request, String appId, AppNavRecord existing) {
    String type = request.type();
    if (!GROUP_TYPE.equals(type) && !ENTRY_TYPE.equals(type)) {
      throw new ValidationException("菜单类型不合法: " + type);
    }
    if (isBlank(request.name())) {
      throw new ValidationException("菜单名称不能为空");
    }
    String name = request.name().trim();
    if (name.length() > 50) {
      throw new ValidationException("菜单名称长度不能超过50个字符");
    }
    if (request.iconName() != null && !NAV_ICON_NAME_PATTERN.matcher(request.iconName()).matches()) {
      throw new ValidationException("菜单图标不合法: " + request.iconName());
    }

    String parentId = isBlank(request.parentId()) ? null : request.parentId();
    NavDestinationRecord destination = null;
    if (ENTRY_TYPE.equals(type)) {
      NavDestinationRequest destinationRequest = request.destination();
      if (destinationRequest == null || isBlank(destinationRequest.type()) || isBlank(destinationRequest.id())) {
        throw new ValidationException("菜单必须选择关联项后才能绑定页面");
      }
      if (!BUILTIN_DESTINATION_TYPE.equals(destinationRequest.type())
        && !FORM_DESTINATION_TYPE.equals(destinationRequest.type())
        && !LINK_DESTINATION_TYPE.equals(destinationRequest.type())) {
        throw new ValidationException("菜单关联类型不合法: " + destinationRequest.type());
      }
      if (BUILTIN_DESTINATION_TYPE.equals(destinationRequest.type()) && !BUILTIN_DESTINATIONS.containsKey(destinationRequest.id())) {
        throw new ValidationException("内置模块不存在: " + destinationRequest.id());
      }
      if (BUILTIN_DESTINATION_TYPE.equals(destinationRequest.type())) {
        destination = new NavDestinationRecord(BUILTIN_DESTINATION_TYPE, destinationRequest.id());
      }
      if (FORM_DESTINATION_TYPE.equals(destinationRequest.type())) {
        requireFormName(appId, destinationRequest.id());
        destination = new NavDestinationRecord(FORM_DESTINATION_TYPE, destinationRequest.id());
      }
      if (LINK_DESTINATION_TYPE.equals(destinationRequest.type())) {
        destination = new NavDestinationRecord(LINK_DESTINATION_TYPE, requireLinkUrl(destinationRequest.id()));
      }
    } else if (request.destination() != null) {
      throw new ValidationException("分组不能绑定关联项");
    }

    String id = existing == null ? generateNavId() : existing.id();
    List<AppNavRecord> children = existing == null
      ? (GROUP_TYPE.equals(type) ? List.of() : List.of())
      : existing.children();
    List<NavVisibilityRule> visibility = request.visibility() == null
      ? (existing == null || existing.visibility() == null ? List.of() : existing.visibility())
      : validatedVisibility(request.visibility());
    return new AppNavRecord(id, type, name, request.iconName(), parentId, destination, children, visibility);
  }

  private List<NavVisibilityRule> validatedVisibility(List<NavVisibilityRule> visibility) {
    if (visibility.size() > 800) throw new ValidationException("菜单授权成员不能超过800项");
    for (NavVisibilityRule rule : visibility) {
      if (rule == null || rule.type() == null
        || !Set.of("USER", "ROLE", "ORGANIZATION").contains(rule.type()) || isBlank(rule.id())) {
        throw new ValidationException("菜单授权成员类型或ID不合法");
      }
      if (Boolean.TRUE.equals(rule.includeChildren()) && !"ORGANIZATION".equals(rule.type())) {
        throw new ValidationException("只有组织支持包含子组织");
      }
    }
    return visibility.stream().distinct().toList();
  }

  private List<AppNavRecord> orderedNavs(List<AppNavRecord> navs, AppNavOrderRequest request) {
    Map<String, AppNavRecord> nodesById = new LinkedHashMap<>();
    navs.forEach(nav -> collectNodes(nav, nodesById));
    List<String> rootIds = request.root();
    Map<String, List<String>> childrenOrder = request.children() == null ? Map.of() : request.children();

    Set<String> expectedRootIds = Set.copyOf(navs.stream().map(AppNavRecord::id).toList());
    if (rootIds.size() != expectedRootIds.size() || !expectedRootIds.containsAll(rootIds)) {
      throw new ValidationException("菜单排序必须包含且仅包含所有根级菜单");
    }
    if (rootIds.stream().distinct().count() != rootIds.size()) {
      throw new ValidationException("根级菜单存在重复排序");
    }

    Map<String, List<String>> expectedChildren = new LinkedHashMap<>();
    collectExpectedChildren(navs, expectedChildren);
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

    List<AppNavRecord> result = new ArrayList<>();
    for (String rootId : rootIds) {
      AppNavRecord nav = nodesById.get(rootId);
      if (GROUP_TYPE.equals(nav.type())) {
        result.add(withChildren(nav, orderedChildren(nav.id(), childrenOrder, nodesById)));
      } else {
        result.add(nav);
      }
    }
    return result;
  }

  private void collectExpectedChildren(
    List<AppNavRecord> navs,
    Map<String, List<String>> expectedChildren
  ) {
    for (AppNavRecord nav : navs) {
      if (GROUP_TYPE.equals(nav.type())) {
        expectedChildren.put(nav.id(), nav.children() == null
          ? List.of()
          : nav.children().stream().map(AppNavRecord::id).toList());
        if (nav.children() != null) collectExpectedChildren(nav.children(), expectedChildren);
      }
    }
  }

  private List<AppNavRecord> orderedChildren(
    String parentId,
    Map<String, List<String>> childrenOrder,
    Map<String, AppNavRecord> nodesById
  ) {
    List<String> orderedIds = childrenOrder.getOrDefault(parentId, List.of());
    return orderedIds.stream()
      .map(nodesById::get)
      .map(child -> GROUP_TYPE.equals(child.type())
        ? withChildren(child, orderedChildren(child.id(), childrenOrder, nodesById))
        : child)
      .toList();
  }

  private List<AppNavResponse> saveNavigation(
    String appId,
    Project project,
    List<AppNavRecord> navs,
    DestinationLabels labels
  ) {
    appNavRecordStore.saveNavigation(project, navs);
    return toResponses(appId, navs, labels);
  }

  private List<AppNavResponse> toResponses(
    String appId,
    List<AppNavRecord> navs,
    DestinationLabels labels) {
    return navs.stream()
      .map(nav -> toResponse(appId, nav, labels))
      .toList();
  }

  private AppNavResponse toResponse(String appId, AppNavRecord nav, DestinationLabels labels) {
    NavDestinationRecord destination = nav.destination();
    NavDestinationResponse destinationResponse = destination == null ? null : new NavDestinationResponse(
      destination.type(),
      destination.id(),
      destinationLabel(destination, labels));
    return new AppNavResponse(
      nav.id(),
      nav.type(),
      nav.name(),
      nav.iconName(),
      nav.parentId(),
      destinationResponse,
      ENTRY_TYPE.equals(nav.type()) ? "/apps/" + appId + "/" + nav.id() : null,
      nav.children() == null
        ? List.of()
        : nav.children().stream().map(child -> toResponse(appId, child, labels)).toList(),
      nav.visibility() == null ? List.of() : nav.visibility(),
      labels.ownerId());
  }

  private String destinationLabel(NavDestinationRecord destination, DestinationLabels labels) {
    if (BUILTIN_DESTINATION_TYPE.equals(destination.type())) {
      return BUILTIN_DESTINATIONS.get(destination.id());
    }
    if (FORM_DESTINATION_TYPE.equals(destination.type())) {
      return labels.forms().getOrDefault(destination.id(), destination.id());
    }
    return destination.id();
  }

  private AppNavRecord requireNav(List<AppNavRecord> navs, String navId) {
    AppNavRecord nav = findNode(navs, navId);
    if (nav == null) {
      throw new NotFoundException("菜单不存在: " + navId);
    }
    return nav;
  }

  private AppNavRecord requireParent(List<AppNavRecord> navs, String parentId) {
    if (parentId == null) {
      return null;
    }
    AppNavRecord parent = findNode(navs, parentId);
    if (parent == null) {
      throw new ValidationException("父级菜单不存在: " + parentId);
    }
    if (!GROUP_TYPE.equals(parent.type())) {
      throw new ValidationException("父级菜单必须是分组");
    }
    return parent;
  }

  private void requireUniqueName(List<AppNavRecord> navs, AppNavRecord nav, String ignoredNavId) {
    boolean duplicated = flatten(navs).stream()
      .filter(item -> !item.id().equals(ignoredNavId))
      .anyMatch(item -> Objects.equals(normalizeParent(item.parentId()), normalizeParent(nav.parentId()))
        && item.name().equals(nav.name()));
    if (duplicated) {
      throw new ValidationException("同级菜单名称已存在: " + nav.name());
    }
  }

  private AppNavRecord findNode(List<AppNavRecord> navs, String navId) {
    for (AppNavRecord nav : navs) {
      if (nav.id().equals(navId)) {
        return nav;
      }
      AppNavRecord child = nav.children() == null ? null : findNode(nav.children(), navId);
      if (child != null) {
        return child;
      }
    }
    return null;
  }

  private List<AppNavRecord> removeNode(List<AppNavRecord> navs, String navId) {
    List<AppNavRecord> result = new ArrayList<>();
    for (AppNavRecord nav : navs) {
      if (nav.id().equals(navId)) {
        continue;
      }
      if (nav.children() == null || nav.children().isEmpty()) {
        result.add(nav);
      } else {
        result.add(withChildren(nav, removeNode(nav.children(), navId)));
      }
    }
    return result;
  }

  private List<AppNavRecord> replaceNode(List<AppNavRecord> navs, AppNavRecord replacement) {
    List<AppNavRecord> result = new ArrayList<>();
    for (AppNavRecord nav : navs) {
      if (nav.id().equals(replacement.id())) {
        result.add(replacement);
      } else if (nav.children() == null || nav.children().isEmpty()) {
        result.add(nav);
      } else {
        result.add(withChildren(nav, replaceNode(nav.children(), replacement)));
      }
    }
    return result;
  }

  private AppNavRecord withChildren(AppNavRecord nav, List<AppNavRecord> children) {
    return new AppNavRecord(
      nav.id(), nav.type(), nav.name(), nav.iconName(), nav.parentId(), nav.destination(), children, nav.visibility());
  }

  private List<AppNavRecord> appended(List<AppNavRecord> children, AppNavRecord child) {
    List<AppNavRecord> result = children == null ? new ArrayList<>() : new ArrayList<>(children);
    result.add(child);
    return result;
  }

  private List<AppNavRecord> flatten(List<AppNavRecord> navs) {
    List<AppNavRecord> result = new ArrayList<>();
    for (AppNavRecord nav : navs) {
      result.add(nav);
      if (nav.children() != null) {
        result.addAll(flatten(nav.children()));
      }
    }
    return result;
  }

  private void collectNodes(AppNavRecord nav, Map<String, AppNavRecord> nodesById) {
    nodesById.put(nav.id(), nav);
    if (nav.children() != null) {
      nav.children().forEach(child -> collectNodes(child, nodesById));
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

  private DestinationLabels destinationLabels(String appId) {
    Project project = appNavRecordStore.requireAppProject(appId);
    return new DestinationLabels(formLabels(appId), project.getOwnerId());
  }

  private void requireFormName(String appId, String formName) {
    appFormService.findForms(appId).stream()
      .filter(form -> form.name().equals(formName))
      .findFirst()
      .orElseThrow(() -> new ValidationException("表单不存在: " + formName));
  }

  private String requireLinkUrl(String value) {
    String url = value == null ? "" : value.trim();
    if (url.isBlank()) {
      throw new ValidationException("菜单链接不能为空");
    }
    if (url.length() > 2048) {
      throw new ValidationException("菜单链接长度不能超过2048个字符");
    }
    URI uri;
    try {
      uri = URI.create(url);
    } catch (IllegalArgumentException exception) {
      throw new ValidationException("菜单链接格式不正确: " + url);
    }
    String scheme = uri.getScheme();
    if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) || isBlank(uri.getHost())) {
      throw new ValidationException("菜单链接必须是 http 或 https 地址: " + url);
    }
    return url;
  }

  private List<NavDestinationResponse> builtInResponses() {
    return BUILTIN_DESTINATIONS.entrySet().stream()
      .map(entry -> new NavDestinationResponse(BUILTIN_DESTINATION_TYPE, entry.getKey(), entry.getValue()))
      .toList();
  }

  private List<NavDestinationResponse> formResponses(String appId) {
    return appFormService.findForms(appId).stream()
      .map(form -> new NavDestinationResponse(FORM_DESTINATION_TYPE, form.name(), form.label()))
      .toList();
  }

  private String normalizeParent(String parentId) {
    return parentId == null ? "" : parentId;
  }

  private String generateNavId() {
    return "nav_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private static Map<String, String> builtInDestinations() {
    Map<String, String> result = new LinkedHashMap<>();
    result.put("workbench", "工作台");
    result.put("files", "文件");
    result.put("approval", "审批中心");
    return result;
  }
}
