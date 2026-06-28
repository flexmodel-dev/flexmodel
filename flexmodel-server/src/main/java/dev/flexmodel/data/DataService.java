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
                                               List<String> expand) {
    return dataRepository.findRecords(projectId, datasourceName, modelName, page, size, filter, sort, expand);
  }

  public long countRecords(String projectId, String datasourceName, String modelName, String filter) {
    return dataRepository.countRecords(projectId, datasourceName, modelName, filter);
  }

  public Map<String, Object> findOneRecord(String projectId, String datasourceName, String modelName, Object id, List<String> expand) {
    return dataRepository.findOneRecord(projectId, datasourceName, modelName, id, expand);
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
                                                        List<String> expand) {
    String datasourceName = projectService.resolveDatabaseName(projectId);
    List<Map<String, Object>> list = dataRepository.findRecords(projectId, datasourceName, modelName, page, size, filter, sort, expand);
    long total = dataRepository.countRecords(projectId, datasourceName, modelName, filter);
    return new PageDTO<>(list, total);
  }

  public Map<String, Object> findOneRecord(String projectId, String modelName, String id, List<String> expand) {
    String datasourceName = projectService.resolveDatabaseName(projectId);
    return dataRepository.findOneRecord(projectId, datasourceName, modelName, id, expand);
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
    Map<String, Object> oldData = dataRepository.findOneRecord(projectId, datasourceName, modelName, id, null);
    Map<String, Object> mergeData = new HashMap<>(oldData);
    mergeData.putAll(record);
    return dataRepository.updateRecord(projectId, datasourceName, modelName, id, mergeData);
  }

  public List<Map<String, Object>> createRecords(String projectId, String modelName, List<Map<String, Object>> data) {
    String datasourceName = projectService.resolveDatabaseName(projectId);
    return dataRepository.createRecords(projectId, datasourceName, modelName, data);
  }

  public List<Map<String, Object>> updateRecords(String projectId, String modelName, List<Map<String, Object>> data) {
    String datasourceName = projectService.resolveDatabaseName(projectId);
    return dataRepository.updateRecords(projectId, datasourceName, modelName, data);
  }

  public long deleteRecords(String projectId, String modelName, List<Object> ids) {
    String datasourceName = projectService.resolveDatabaseName(projectId);
    return dataRepository.deleteRecords(projectId, datasourceName, modelName, ids);
  }

}
