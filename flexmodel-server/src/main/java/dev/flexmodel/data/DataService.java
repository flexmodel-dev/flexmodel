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
    if ("f_runtime_message".equals(modelName)) throw new jakarta.ws.rs.NotFoundException("模型不存在");
    return dataRepository.findRecords(projectId, datasourceName, modelName, page, size, filter, sort, expand);
  }

  public long countRecords(String projectId, String datasourceName, String modelName, String filter) {
    if ("f_runtime_message".equals(modelName)) throw new jakarta.ws.rs.NotFoundException("模型不存在");
    return dataRepository.countRecords(projectId, datasourceName, modelName, filter);
  }

  public long countRecords(String projectId, String modelName, String filter) {
    if ("f_runtime_message".equals(modelName)) throw new jakarta.ws.rs.NotFoundException("模型不存在");
    return dataRepository.countRecords(
      projectId, projectService.resolveDatabaseName(projectId), modelName, filter);
  }

  public List<Map<String, Object>> aggregateRecords(
    String projectId,
    String datasourceName,
    String modelName,
    String dimensionField,
    String measureField,
    String aggregation,
    String filter,
    int limit
  ) {
    if ("f_runtime_message".equals(modelName)) throw new jakarta.ws.rs.NotFoundException("模型不存在");
    return dataRepository.aggregateRecords(
      projectId, datasourceName, modelName, dimensionField, measureField, aggregation, filter, limit);
  }

  public List<Map<String, Object>> aggregateRecords(
    String projectId,
    String datasourceName,
    String modelName,
    String dimensionField,
    String measureField,
    String aggregation,
    int limit
  ) {
    if ("f_runtime_message".equals(modelName)) throw new jakarta.ws.rs.NotFoundException("模型不存在");
    return dataRepository.aggregateRecords(
      projectId, datasourceName, modelName, dimensionField, measureField, aggregation, limit);
  }

  public List<Map<String, Object>> aggregateRecords(
    String projectId,
    String modelName,
    String dimensionField,
    String measureField,
    String aggregation,
    int limit
  ) {
    if ("f_runtime_message".equals(modelName)) throw new jakarta.ws.rs.NotFoundException("模型不存在");
    String datasourceName = projectService.resolveDatabaseName(projectId);
    return aggregateRecords(
      projectId, datasourceName, modelName, dimensionField, measureField, aggregation, limit);
  }

  public Map<String, Object> findOneRecord(String projectId, String datasourceName, String modelName, Object id, List<String> expand) {
    if ("f_runtime_message".equals(modelName)) throw new jakarta.ws.rs.NotFoundException("模型不存在");
    return dataRepository.findOneRecord(projectId, datasourceName, modelName, id, expand);
  }

  public Map<String, Object> createRecord(String projectId, String datasourceName, String modelName, Map<String, Object> data) {
    if ("f_runtime_message".equals(modelName)) throw new jakarta.ws.rs.NotFoundException("模型不存在");
    return dataRepository.createRecord(projectId, datasourceName, modelName, data);
  }

  public Map<String, Object> updateRecord(String projectId, String datasourceName, String modelName, Object id, Map<String, Object> data) {
    if ("f_runtime_message".equals(modelName)) throw new jakarta.ws.rs.NotFoundException("模型不存在");
    return dataRepository.updateRecord(projectId, datasourceName, modelName, id, data);
  }

  public void deleteRecord(String projectId, String datasourceName, String modelName, Object id) {
    if ("f_runtime_message".equals(modelName)) throw new jakarta.ws.rs.NotFoundException("模型不存在");
    dataRepository.deleteRecord(projectId, datasourceName, modelName, id);
  }

  public PageDTO<Map<String, Object>> findPagingRecords(String projectId,
                                                        String modelName,
                                                        int page,
                                                        int size,
                                                        String filter,
                                                        String sort,
                                                        List<String> expand) {
    if ("f_runtime_message".equals(modelName)) throw new jakarta.ws.rs.NotFoundException("模型不存在");
    String datasourceName = projectService.resolveDatabaseName(projectId);
    return findPagingRecords(projectId, datasourceName, modelName, page, size, filter, sort, expand);
  }

  public PageDTO<Map<String, Object>> findPagingRecords(String projectId,
                                                        String datasourceName,
                                                        String modelName,
                                                        int page,
                                                        int size,
                                                        String filter,
                                                        String sort,
                                                        List<String> expand) {
    if ("f_runtime_message".equals(modelName)) throw new jakarta.ws.rs.NotFoundException("模型不存在");
    List<Map<String, Object>> list = dataRepository.findRecords(projectId, datasourceName, modelName, page, size, filter, sort, expand);
    long total = dataRepository.countRecords(projectId, datasourceName, modelName, filter);
    return new PageDTO<>(list, total);
  }

  public Map<String, Object> findOneRecord(String projectId, String modelName, String id, List<String> expand) {
    if ("f_runtime_message".equals(modelName)) throw new jakarta.ws.rs.NotFoundException("模型不存在");
    String datasourceName = projectService.resolveDatabaseName(projectId);
    return dataRepository.findOneRecord(projectId, datasourceName, modelName, id, expand);
  }

  public Map<String, Object> createRecord(String projectId, String modelName, Map<String, Object> data) {
    if ("f_runtime_message".equals(modelName)) throw new jakarta.ws.rs.NotFoundException("模型不存在");
    String datasourceName = projectService.resolveDatabaseName(projectId);
    return dataRepository.createRecord(projectId, datasourceName, modelName, data);
  }

  public Map<String, Object> updateRecord(String projectId, String modelName, Object id, Map<String, Object> data) {
    if ("f_runtime_message".equals(modelName)) throw new jakarta.ws.rs.NotFoundException("模型不存在");
    String datasourceName = projectService.resolveDatabaseName(projectId);
    return dataRepository.updateRecord(projectId, datasourceName, modelName, id, data);
  }

  public void deleteRecord(String projectId, String modelName, Object id) {
    if ("f_runtime_message".equals(modelName)) throw new jakarta.ws.rs.NotFoundException("模型不存在");
    String datasourceName = projectService.resolveDatabaseName(projectId);
    dataRepository.deleteRecord(projectId, datasourceName, modelName, id);
  }

  public Map<String, Object> updateRecordIgnoreNull(String projectId, String modelName, String id, Map<String, Object> record) {
    if ("f_runtime_message".equals(modelName)) throw new jakarta.ws.rs.NotFoundException("模型不存在");
    String datasourceName = projectService.resolveDatabaseName(projectId);
    Map<String, Object> oldData = dataRepository.findOneRecord(projectId, datasourceName, modelName, id, null);
    Map<String, Object> mergeData = new HashMap<>(oldData);
    mergeData.putAll(record);
    return dataRepository.updateRecord(projectId, datasourceName, modelName, id, mergeData);
  }

  public List<Map<String, Object>> createRecords(String projectId, String modelName, List<Map<String, Object>> data) {
    if ("f_runtime_message".equals(modelName)) throw new jakarta.ws.rs.NotFoundException("模型不存在");
    String datasourceName = projectService.resolveDatabaseName(projectId);
    return dataRepository.createRecords(projectId, datasourceName, modelName, data);
  }

  public List<Map<String, Object>> updateRecords(String projectId, String modelName, List<Map<String, Object>> data) {
    if ("f_runtime_message".equals(modelName)) throw new jakarta.ws.rs.NotFoundException("模型不存在");
    String datasourceName = projectService.resolveDatabaseName(projectId);
    return dataRepository.updateRecords(projectId, datasourceName, modelName, data);
  }

  public long deleteRecords(String projectId, String modelName, List<Object> ids) {
    if ("f_runtime_message".equals(modelName)) throw new jakarta.ws.rs.NotFoundException("模型不存在");
    String datasourceName = projectService.resolveDatabaseName(projectId);
    return dataRepository.deleteRecords(projectId, datasourceName, modelName, ids);
  }

}
