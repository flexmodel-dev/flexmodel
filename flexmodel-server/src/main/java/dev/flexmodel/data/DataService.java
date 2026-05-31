package dev.flexmodel.data;

import dev.flexmodel.common.dto.PageDTO;
import dev.flexmodel.project.ProjectService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author cjbi
 */
@ApplicationScoped
public class DataService {

  @Inject
  DataRepository dataRepository;

  @Inject
  ProjectService projectService;

  public List<Map<String, Object>> findRecords(String projectId,
                                               String datasourceName,
                                               String modelName,
                                               Integer page,
                                               Integer size,
                                               String filter,
                                               String sort,
                                               boolean nestedQueryEnabled) {
    return dataRepository.findRecords(projectId, datasourceName, modelName, page, size, filter, sort, nestedQueryEnabled);
  }

  public long countRecords(String projectId, String datasourceName, String modelName, String filter) {
    return dataRepository.countRecords(projectId, datasourceName, modelName, filter);
  }

  public Map<String, Object> findOneRecord(String projectId, String datasourceName, String modelName, Object id, boolean nestedQuery) {
    return dataRepository.findOneRecord(projectId, datasourceName, modelName, id, nestedQuery);
  }

  public Map<String, Object> createRecord(String projectId, String datasourceName, String modelName, Map<String, Object> data) {
    return dataRepository.createRecord(projectId, datasourceName, modelName, data);
  }

  public Map<String, Object> updateRecord(String projectId, String datasourceName, String modelName, Object id, Map<String, Object> data) {
    return dataRepository.updateRecord(projectId, datasourceName, modelName, id, data);
  }

  public void deleteRecord(String projectId, String datasourceName, String modelName, Object id) {
    dataRepository.deleteRecord(projectId, datasourceName, modelName, id);
  }

  public PageDTO<Map<String, Object>> findPagingRecords(String projectId,
                                                        String modelName,
                                                        int page,
                                                        int size,
                                                        String filter,
                                                        String sort,
                                                        boolean nestedQuery) {
    String datasourceName = projectService.resolveDatabaseName(projectId);
    List<Map<String, Object>> list = dataRepository.findRecords(projectId, datasourceName, modelName, page, size, filter, sort, nestedQuery);
    long total = dataRepository.countRecords(projectId, datasourceName, modelName, filter);
    return new PageDTO<>(list, total);
  }

  public Map<String, Object> findOneRecord(String projectId, String modelName, String id, boolean nestedQuery) {
    String datasourceName = projectService.resolveDatabaseName(projectId);
    return dataRepository.findOneRecord(projectId, datasourceName, modelName, id, nestedQuery);
  }

  public Map<String, Object> createRecord(String projectId, String modelName, Map<String, Object> data) {
    String datasourceName = projectService.resolveDatabaseName(projectId);
    return dataRepository.createRecord(projectId, datasourceName, modelName, data);
  }

  public Map<String, Object> updateRecord(String projectId, String modelName, Object id, Map<String, Object> data) {
    String datasourceName = projectService.resolveDatabaseName(projectId);
    return dataRepository.updateRecord(projectId, datasourceName, modelName, id, data);
  }

  public void deleteRecord(String projectId, String modelName, Object id) {
    String datasourceName = projectService.resolveDatabaseName(projectId);
    dataRepository.deleteRecord(projectId, datasourceName, modelName, id);
  }

  public Map<String, Object> updateRecordIgnoreNull(String projectId, String modelName, String id, Map<String, Object> record) {
    String datasourceName = projectService.resolveDatabaseName(projectId);
    Map<String, Object> oldData = dataRepository.findOneRecord(projectId, datasourceName, modelName, id, false);
    Map<String, Object> mergeData = new HashMap<>(oldData);
    mergeData.putAll(record);
    return dataRepository.updateRecord(projectId, datasourceName, modelName, id, mergeData);
  }

}
