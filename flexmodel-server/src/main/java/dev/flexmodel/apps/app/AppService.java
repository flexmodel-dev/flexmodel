package dev.flexmodel.apps.app;

import dev.flexmodel.apps.app.dto.AppCreateRequest;
import dev.flexmodel.apps.app.dto.AppResponse;
import dev.flexmodel.apps.app.dto.AppUpdateRequest;
import dev.flexmodel.apps.approval.AppApprovalLifecycle;
import dev.flexmodel.apps.dataauthorization.AppDataAuthorizationService;
import dev.flexmodel.apps.workbench.AppWorkbenchRepository;
import dev.flexmodel.auth.service.UserService;
import dev.flexmodel.codegen.entity.App;
import dev.flexmodel.codegen.entity.Project;
import dev.flexmodel.codegen.entity.User;
import dev.flexmodel.common.NotFoundException;
import dev.flexmodel.common.SessionContext;
import dev.flexmodel.common.ValidationException;
import dev.flexmodel.project.ProjectService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 应用管理服务。应用（f_app）与项目（f_project）一对一共享主键。
 *
 * @author cjbi
 */
@ApplicationScoped
public class AppService {
  @Inject
  AppApprovalLifecycle appApprovalLifecycle;

  private static final Pattern PROJECT_ID_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{1,62}$");
  private static final Pattern PRIMARY_COLOR_PATTERN = Pattern.compile("^[a-z][a-z0-9-]{0,31}$");
  private static final Pattern ICON_NAME_PATTERN = Pattern.compile("^[A-Z][A-Za-z0-9]{0,63}$");
  private static final String DEFAULT_ICON_NAME = "Boxes";
  private static final String DEFAULT_PRIMARY_COLOR = "blue";

  /**
   * f_app.provisioning 状态：导入重建期间应用对列表和普通访问不可见。
   */
  public static final String PROVISIONING_IMPORTING = "importing";

  /**
   * 整包导入在同一线程内重建应用，需临时绕过 provisioning 访问守卫。
   */
  public static final ThreadLocal<Boolean> IMPORT_BYPASS = ThreadLocal.withInitial(() -> Boolean.FALSE);

  /**
   * 是否处于导入重建中（应用尚未激活）。
   */
  public static boolean isImporting(App app) {
    return app != null && PROVISIONING_IMPORTING.equals(app.getProvisioning());
  }

  public static boolean importBypassed() {
    return Boolean.TRUE.equals(IMPORT_BYPASS.get());
  }

  @Inject
  ProjectService projectService;

  @Inject
  AppRepository appRepository;

  @Inject
  AppWorkbenchRepository appWorkbenchRepository;

  @Inject
  SessionContext sessionContext;

  @Inject
  UserService userService;

  @Inject
  AppDataAuthorizationService appDataAuthorizationService;

  public List<AppResponse> findApps() {
    Map<String, String> ownerNames = userService.findAll().stream()
      .collect(Collectors.toMap(User::getId, user -> userDisplayName(user, user.getId())));
    return appRepository.findApps().stream()
      .filter(app -> !isImporting(app))
      .map(app -> {
        Project project = projectService.findProject(app.getId());
        if (project == null) {
          return null;
        }
        return toResponse(project, app, ownerDisplayName(project.getOwnerId(), ownerNames));
      })
      .filter(Objects::nonNull)
      .toList();
  }

  public AppResponse findApp(String appId) {
    Project project = requireAppProject(appId);
    App app = requireApp(appId);
    return toResponse(project, app);
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

    String userId = currentUserId();
    project.setCreatedBy(userId);
    project.setUpdatedBy(userId);
    project.setCreatedAt(LocalDateTime.now());
    project.setUpdatedAt(LocalDateTime.now());

    Project created = projectService.createProject(project);

    App app = new App();
    app.setId(created.getId());
    app.setIconName(iconName(request.iconName()));
    app.setPrimaryColor(primaryColor(request.primaryColor()));
    app.setNavigation(List.of());
    app.setForms(List.of());
    app.setCreatedBy(userId);
    app.setUpdatedBy(userId);
    app.setCreatedAt(LocalDateTime.now());
    app.setUpdatedAt(LocalDateTime.now());
    appRepository.save(app);

    return toResponse(created, app, iconName(request.iconName()), primaryColor(request.primaryColor()));
  }

  public AppResponse updateApp(String appId, AppUpdateRequest request) {
    validateUpdateRequest(request);

    Project project = requireAppProject(appId);
    project.setName(request.name().trim());
    if (request.description() != null) {
      project.setDescription(request.description());
    }
    project.setUpdatedBy(currentUserId());
    project.setUpdatedAt(LocalDateTime.now());

    Project updated = projectService.updateProject(project);

    App app = requireApp(appId);
    app.setIconName(iconName(request.iconName(), app));
    app.setPrimaryColor(primaryColor(request.primaryColor(), app));
    app.setUpdatedBy(currentUserId());
    app.setUpdatedAt(LocalDateTime.now());
    appRepository.save(app);

    return toResponse(updated, app);
  }

  public void deleteApp(String appId) {
    Project project = requireAppProject(appId);
    appApprovalLifecycle.beforeDelete(appId);
    appWorkbenchRepository.deleteByAppId(project.getId());
    appDataAuthorizationService.deleteAppRules(appId);
    projectService.deleteProject(project.getId());
  }

  private Project requireAppProject(String appId) {
    if ("default".equals(appId)) {
      throw new NotFoundException("应用不存在: " + appId);
    }
    App app = appRepository.findApp(appId);
    if (app == null) {
      throw new NotFoundException("应用不存在: " + appId);
    }
    Project project = projectService.findProject(appId);
    if (project == null || project.getParentProjectId() != null) {
      throw new NotFoundException("应用不存在: " + appId);
    }
    return project;
  }

  private App requireApp(String appId) {
    App app = appRepository.findApp(appId);
    if (app == null) {
      throw new NotFoundException("应用不存在: " + appId);
    }
    return app;
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
    if (iconName != null && !ICON_NAME_PATTERN.matcher(iconName).matches()) {
      throw new ValidationException("应用图标不合法: " + iconName);
    }
  }

  private void validatePrimaryColor(String primaryColor) {
    if (primaryColor != null && !PRIMARY_COLOR_PATTERN.matcher(primaryColor).matches()) {
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

  private String iconName(String requestedIconName) {
    return requestedIconName == null ? DEFAULT_ICON_NAME : requestedIconName;
  }

  private String iconName(String requestedIconName, App app) {
    if (requestedIconName != null) {
      return requestedIconName;
    }
    return isBlank(app.getIconName()) ? DEFAULT_ICON_NAME : app.getIconName();
  }

  private String primaryColor(String requestedPrimaryColor) {
    return requestedPrimaryColor == null ? DEFAULT_PRIMARY_COLOR : requestedPrimaryColor;
  }

  private String primaryColor(String requestedPrimaryColor, App app) {
    if (requestedPrimaryColor != null) {
      return requestedPrimaryColor;
    }
    return isBlank(app.getPrimaryColor()) ? DEFAULT_PRIMARY_COLOR : app.getPrimaryColor();
  }

  private AppResponse toResponse(Project project, App app) {
    return toResponse(project, app, ownerDisplayName(project.getOwnerId()));
  }

  private AppResponse toResponse(Project project, App app, String owner) {
    return toResponse(project, app, iconName(null, app), primaryColor(null, app), owner);
  }

  private AppResponse toResponse(Project project, App app, String iconName, String primaryColor) {
    return toResponse(project, app, iconName, primaryColor, ownerDisplayName(project.getOwnerId()));
  }

  private AppResponse toResponse(Project project, App app, String iconName, String primaryColor, String owner) {
    return AppResponse.from(project, owner, iconName, primaryColor, app.getUpdatedAt());
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
