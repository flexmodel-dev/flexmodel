package dev.flexmodel.data;

import java.util.List;
import java.util.Map;

/**
 * @author cjbi
 */
public interface DataRepository {

  List<Map<String, Object>> findRecords(String projectId,
                                        String datasourceName,
                                        String modelName,
                                        Integer page,
                                        Integer size,
                                        String filter,
                                        String sort,
                                        List<String> expand);

  long countRecords(String projectId, String datasourceName, String modelName, String filter);

  List<Map<String, Object>> aggregateRecords(
    String projectId,
    String datasourceName,
    String modelName,
    String dimensionField,
    String measureField,
    String aggregation,
    int limit
  );

  default List<Map<String, Object>> aggregateRecords(
    String projectId,
    String datasourceName,
    String modelName,
    String dimensionField,
    String measureField,
    String aggregation,
    String filter,
    int limit
  ) {
    return aggregateRecords(projectId, datasourceName, modelName, dimensionField, measureField, aggregation, limit);
  }

  Map<String, Object> findOneRecord(String projectId, String datasourceName, String modelName, Object id, List<String> expand);

  Map<String, Object> createRecord(String projectId, String datasourceName, String modelName, Map<String, Object> data);

  Map<String, Object> updateRecord(String projectId, String datasourceName, String modelName, Object id, Map<String, Object> data);

  void deleteRecord(String projectId, String datasourceName, String modelName, Object id);

  List<Map<String, Object>> createRecords(String projectId, String datasourceName, String modelName, List<Map<String, Object>> data);

  List<Map<String, Object>> updateRecords(String projectId, String datasourceName, String modelName, List<Map<String, Object>> data);

  long deleteRecords(String projectId, String datasourceName, String modelName, List<Object> ids);
}
