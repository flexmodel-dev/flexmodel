package dev.flexmodel.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import dev.flexmodel.application.dto.PageDTO;
import dev.flexmodel.codegen.entity.Project;
import dev.flexmodel.domain.model.auth.ProjectService;
import dev.flexmodel.domain.model.data.DataService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author cjbi
 */
@ApplicationScoped
public class DataApplicationService {

  @Inject
  DataService dataService;

  @Inject
  ProjectService projectService;

  public PageDTO<Map<String, Object>> findPagingRecords(String projectId,
                                                        String modelName,
                                                        int page,
                                                        int size,
                                                        String filter,
                                                        String sort,
                                                        boolean nestedQuery) {
    Project project = projectService.findProject(projectId);
    String datasourceName = project.getDatabaseName();
    List<Map<String, Object>> list = dataService.findRecords(projectId, datasourceName, modelName, page, size, filter, sort, nestedQuery);
    long total = dataService.countRecords(projectId, datasourceName, modelName, filter);
    return new PageDTO<>(list, total);
  }

  public Map<String, Object> findOneRecord(String projectId, String modelName, String id, boolean nestedQuery) {
    Project project = projectService.findProject(projectId);
    String datasourceName = project.getDatabaseName();
    return dataService.findOneRecord(projectId, datasourceName, modelName, id, nestedQuery);
  }

  public Map<String, Object> createRecord(String projectId, String modelName, Map<String, Object> data) {
    Project project = projectService.findProject(projectId);
    String datasourceName = project.getDatabaseName();
    return dataService.createRecord(projectId, datasourceName, modelName, data);
  }

  public Map<String, Object> updateRecord(String projectId, String modelName, Object id, Map<String, Object> data) {
    Project project = projectService.findProject(projectId);
    String datasourceName = project.getDatabaseName();
    return dataService.updateRecord(projectId, datasourceName, modelName, id, data);
  }

  public void deleteRecord(String projectId, String modelName, Object id) {
    Project project = projectService.findProject(projectId);
    String datasourceName = project.getDatabaseName();
    dataService.deleteRecord(projectId, datasourceName, modelName, id);
  }

  public Map<String, Object> updateRecordIgnoreNull(String projectId, String modelName, String id, Map<String, Object> record) {
    Project project = projectService.findProject(projectId);
    String datasourceName = project.getDatabaseName();
    Map<String, Object> oldData = dataService.findOneRecord(projectId, datasourceName, modelName, id, false);
    Map<String, Object> mergeData = new HashMap<>(oldData);
    mergeData.putAll(record);
    return dataService.updateRecord(projectId, datasourceName, modelName, id, mergeData);
  }
}
