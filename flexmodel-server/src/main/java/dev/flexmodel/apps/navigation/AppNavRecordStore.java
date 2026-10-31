package dev.flexmodel.apps.navigation;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.apps.app.AppRepository;
import dev.flexmodel.apps.app.AppService;
import dev.flexmodel.apps.navigation.dto.AppNavRecord;
import dev.flexmodel.codegen.entity.App;
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
import java.util.List;

/**
 * 应用导航的读取与持久化，导航存储在 f_app.navigation。
 */
@ApplicationScoped
public class AppNavRecordStore {

  @Inject
  ProjectService projectService;

  @Inject
  AppRepository appRepository;

  @Inject
  SessionContext sessionContext;

  /**
   * 校验 appId 对应一个可访问的应用（存在 f_app 行、为顶层项目、非导入重建中），返回其项目。
   */
  public Project requireAppProject(String appId) {
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
    if (AppService.isImporting(app) && !AppService.importBypassed()) {
      throw new NotFoundException("应用不存在: " + appId);
    }
    return project;
  }

  public List<AppNavRecord> navRecords(Project project) {
    Object value = navigationValue(project.getId());
    if (value == null) {
      return new ArrayList<>();
    }
    if (!(value instanceof Collection<?> collection)) {
      throw new ValidationException("应用导航数据格式不正确");
    }
    List<AppNavRecord> result = new ArrayList<>();
    for (Object item : collection) {
      AppNavRecord nav = JsonUtils.convertValue(item, AppNavRecord.class);
      if (nav == null || isBlank(nav.id()) || isBlank(nav.type()) || isBlank(nav.name())) {
        throw new ValidationException("应用导航数据格式不正确");
      }
      result.add(nav);
    }
    return result;
  }

  public void saveNavigation(Project project, List<AppNavRecord> navs) {
    App app = appRepository.findApp(project.getId());
    if (app == null) {
      throw new NotFoundException("应用不存在: " + project.getId());
    }
    app.setNavigation(navs);
    app.setUpdatedBy(currentUserId());
    app.setUpdatedAt(LocalDateTime.now());
    appRepository.save(app);
  }

  private Object navigationValue(String appId) {
    App app = appRepository.findApp(appId);
    return app == null ? null : app.getNavigation();
  }

  private String currentUserId() {
    String userId = sessionContext.getUserId();
    return isBlank(userId) ? "admin" : userId;
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
