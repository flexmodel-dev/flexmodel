package dev.flexmodel.apps;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.apps.approval.AppApprovalLifecycle;
import dev.flexmodel.apps.dto.AppCreateRequest;
import dev.flexmodel.apps.dto.AppResponse;
import dev.flexmodel.apps.dto.AppUpdateRequest;
import dev.flexmodel.auth.service.UserService;
import dev.flexmodel.codegen.entity.Project;
import dev.flexmodel.codegen.entity.User;
import dev.flexmodel.common.NotFoundException;
import dev.flexmodel.common.SessionContext;
import dev.flexmodel.common.ValidationException;
import dev.flexmodel.project.ProjectService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 应用管理服务，应用与项目一一对齐。
 *
 * @author cjbi
 */
@ApplicationScoped
public class AppService {
  @Inject
  AppApprovalLifecycle appApprovalLifecycle;

  private static final Pattern PROJECT_ID_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{1,62}$");
  private static final Set<String> ICON_NAMES = Set.of(
    "ShoppingCart", "Banknote", "Boxes", "Folder", "Tags", "TrendingUp");
  private static final List<String> PRIMARY_COLORS = List.of(
    "blue", "indigo", "green", "amber", "rose", "black");
  private static final String DEFAULT_ICON_NAME = "Boxes";
  private static final String DEFAULT_PRIMARY_COLOR = "blue";
  private static final String METADATA_APP_KEY = "app";

  @Inject
  ProjectService projectService;

  @Inject
  WorkbenchRepository workbenchRepository;

  @Inject
  SessionContext sessionContext;

  @Inject
  UserService userService;

  @Inject
  AppDataAuthorizationService appDataAuthorizationService;

  public List<AppResponse> findApps() {
    Map<String, String> ownerNames = userService.findAll().stream()
      .collect(Collectors.toMap(User::getId, user -> userDisplayName(user, user.getId())));
    return projectService.findTopLevelProjects().stream()
      .map(project -> toResponse(
        project,
        iconName(project, null),
        primaryColor(project, null),
        ownerDisplayName(project.getOwnerId(), ownerNames)))
      .toList();
  }

  public AppResponse findApp(String appId) {
    return toResponse(requireAppProject(appId));
  }

  public AppResponse createApp(AppCreateRequest request) {
    validateCreateRequest(request);

    String projectId = request.projectId();
    if (isBlank(projectId)) {
      projectId = generateProjectId(request.name());
    }
    if (projectService.findProject(projectId) != null) {
      throw new ValidationException("项目ID已存在: " + projectId);
    }

    Project project = new Project();
    project.setId(projectId);
    project.setName(request.name().trim());
    project.setDescription(request.description() == null ? "" : request.description());
    project.setEnabled(true);
    project.setMetadata(Map.of(METADATA_APP_KEY, appMetadata(request.iconName(), request.primaryColor())));

    String userId = currentUserId();
    project.setCreatedBy(userId);
    project.setUpdatedBy(userId);
    project.setCreatedAt(LocalDateTime.now());
    project.setUpdatedAt(LocalDateTime.now());

    Project created = projectService.createProject(project);
    return toResponse(created, iconName(created, request.iconName()), primaryColor(created, request.primaryColor()));
  }

  public AppResponse updateApp(String appId, AppUpdateRequest request) {
    validateUpdateRequest(request);

    Project project = requireAppProject(appId);
    project.setName(request.name().trim());
    if (request.description() != null) {
      project.setDescription(request.description());
    }

    Map<String, Object> metadata = metadataMap(project);
    Map<String, Object> app = appNode(project);
    app.put("iconName", iconName(project, request.iconName()));
    app.put("primaryColor", primaryColor(project, request.primaryColor()));
    metadata.put(METADATA_APP_KEY, app);
    project.setMetadata(metadata);

    project.setUpdatedBy(currentUserId());
    project.setUpdatedAt(LocalDateTime.now());

    Project updated = projectService.updateProject(project);
    return toResponse(updated);
  }

  public void deleteApp(String appId) {
    Project project = requireAppProject(appId);
    appApprovalLifecycle.beforeDelete(appId);
    workbenchRepository.deleteByProjectId(project.getId());
    appDataAuthorizationService.deleteAppRules(appId);
    projectService.deleteProject(project.getId());
  }

  private Project requireAppProject(String appId) {
    Project project = projectService.findProject(appId);
    if (project == null || project.getParentProjectId() != null || "default".equals(appId)) {
      throw new NotFoundException("应用不存在: " + appId);
    }
    return project;
  }

  private void validateCreateRequest(AppCreateRequest request) {
    validateName(request.name());
    validateDescription(request.description());
    if (request.projectId() != null && !PROJECT_ID_PATTERN.matcher(request.projectId()).matches()) {
      throw new ValidationException("项目ID格式不正确");
    }
    validateIconName(request.iconName());
    validatePrimaryColor(request.primaryColor());
  }

  private void validateUpdateRequest(AppUpdateRequest request) {
    validateName(request.name());
    validateDescription(request.description());
    validateIconName(request.iconName());
    validatePrimaryColor(request.primaryColor());
  }

  private void validateName(String name) {
    if (isBlank(name)) {
      throw new ValidationException("应用名称不能为空");
    }
    if (name.trim().length() > 100) {
      throw new ValidationException("应用名称长度不能超过100个字符");
    }
  }

  private void validateDescription(String description) {
    if (description != null && description.length() > 500) {
      throw new ValidationException("应用描述长度不能超过500个字符");
    }
  }

  private void validateIconName(String iconName) {
    if (iconName != null && !ICON_NAMES.contains(iconName)) {
      throw new ValidationException("应用图标不合法: " + iconName);
    }
  }

  private void validatePrimaryColor(String primaryColor) {
    if (primaryColor != null && !PRIMARY_COLORS.contains(primaryColor)) {
      throw new ValidationException("应用主色不合法: " + primaryColor);
    }
  }

  private String generateProjectId(String name) {
    String normalized = name == null
      ? ""
      : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_")
      .replaceFirst("^_+", "")
      .replaceFirst("_+$", "");
    if (normalized.length() < 2 || !Character.isLetter(normalized.charAt(0))) {
      normalized = "app_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
    if (normalized.length() > 63) {
      normalized = normalized.substring(0, 63);
    }

    String candidate = normalized;
    int suffix = 1;
    while (projectService.findProject(candidate) != null) {
      String suffixText = "_" + suffix++;
      int maxLength = 63 - suffixText.length();
      candidate = normalized.substring(0, Math.min(normalized.length(), maxLength)) + suffixText;
    }
    return candidate;
  }

  private Map<String, Object> appMetadata(String iconName, String primaryColor) {
    Map<String, Object> app = new LinkedHashMap<>();
    app.put("iconName", iconName == null ? DEFAULT_ICON_NAME : iconName);
    app.put("primaryColor", primaryColor == null ? DEFAULT_PRIMARY_COLOR : primaryColor);
    app.put("menus", List.of());
    return app;
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
      return Map.of();
    }
    if (app instanceof Map<?, ?> map) {
      Map<String, Object> result = new LinkedHashMap<>();
      map.forEach((key, value) -> result.put(String.valueOf(key), value));
      return result;
    }
    return JsonUtils.convertValue(app, Map.class);
  }

  private String iconName(Project project, String requestedIconName) {
    if (requestedIconName != null) {
      return requestedIconName;
    }
    Object value = appNode(project).get("iconName");
    return value == null ? DEFAULT_ICON_NAME : String.valueOf(value);
  }

  private String primaryColor(Project project, String requestedPrimaryColor) {
    if (requestedPrimaryColor != null) {
      return requestedPrimaryColor;
    }
    Object value = appNode(project).get("primaryColor");
    return value == null ? DEFAULT_PRIMARY_COLOR : String.valueOf(value);
  }

  private AppResponse toResponse(Project project) {
    return toResponse(project, iconName(project, null), primaryColor(project, null));
  }

  private AppResponse toResponse(Project project, String iconName, String primaryColor) {
    return toResponse(project, iconName, primaryColor, ownerDisplayName(project.getOwnerId()));
  }

  private AppResponse toResponse(Project project, String iconName, String primaryColor, String owner) {
    return AppResponse.from(project, owner, iconName, primaryColor);
  }

  private String ownerDisplayName(String ownerId) {
    String id = isBlank(ownerId) ? "admin" : ownerId;
    User user = userService.findById(id);
    return userDisplayName(user, id);
  }

  private String ownerDisplayName(String ownerId, Map<String, String> ownerNames) {
    String id = isBlank(ownerId) ? "admin" : ownerId;
    return ownerNames.getOrDefault(id, id);
  }

  private String userDisplayName(User user, String fallback) {
    return user == null || isBlank(user.getName()) ? fallback : user.getName();
  }

  private String currentUserId() {
    String userId = sessionContext.getUserId();
    return isBlank(userId) ? "admin" : userId;
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
