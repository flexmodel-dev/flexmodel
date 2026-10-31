package dev.flexmodel.apps;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.apps.dto.AppNavRecord;
import dev.flexmodel.codegen.entity.Project;
import dev.flexmodel.common.NotFoundException;
import dev.flexmodel.common.SessionContext;
import dev.flexmodel.common.ValidationException;
import dev.flexmodel.project.ProjectService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDateTime;
import java.util.*;

@ApplicationScoped
public class AppNavRecordStore {

  private static final String METADATA_APP_KEY = "app";

  @Inject
  ProjectService projectService;

  @Inject
  SessionContext sessionContext;

  public Project requireAppProject(String appId) {
    Project project = projectService.findProject(appId);
    if (project == null || project.getParentProjectId() != null || "default".equals(appId)) {
      throw new NotFoundException("应用不存在: " + appId);
    }
    if (AppService.isImporting(project) && !AppService.importBypassed()) {
      throw new NotFoundException("应用不存在: " + appId);
    }
    return project;
  }

  public List<AppNavRecord> navRecords(Project project) {
    Object value = appNode(project).get("navigation");
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

  public Project saveNavigation(Project project, List<AppNavRecord> navs) {
    Map<String, Object> metadata = metadataMap(project);
    Map<String, Object> app = appNode(project);
    app.put("navigation", navs);
    metadata.put(METADATA_APP_KEY, app);
    project.setMetadata(metadata);
    project.setUpdatedBy(currentUserId());
    project.setUpdatedAt(LocalDateTime.now());
    return projectService.updateProject(project);
  }

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

  private String currentUserId() {
    String userId = sessionContext.getUserId();
    return isBlank(userId) ? "admin" : userId;
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
