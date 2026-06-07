package dev.flexmodel.mcp;

import dev.flexmodel.common.dto.PageDTO;
import dev.flexmodel.JsonUtils;
import dev.flexmodel.data.DataService;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Map;

/**
 * MCP 工具：数据操作
 * 提供记录的分页查询、单条查询、创建、更新和删除能力
 */
public class DataTools {

  private static final Logger log = Logger.getLogger(DataTools.class);

  @Inject
  DataService dataService;


  @Tool(description = """
    Query records of a model with pagination, optional filtering and sorting. \
    Returns a paginated result: {total, list}. \
    Filter DSL (JSON): same-level fields are implicitly AND-ed. \
    Comparison operators: _eq, _ne, _gt, _gte, _lt, _lte. \
    String operators: _contains, _not_contains, _starts_with, _ends_with. \
    Collection operators: _in, _nin. Range: _between. Logical: _and, _or. \
    Shorthand: {"field": value} equals {"field": {"_eq": value}}. \
    Dot path for related fields: {"studentClass.className": {"_contains": "CS"}}. \
    Filter examples: {"age": {"_gte": 18}} | \
    {"status": {"_in": ["active", "pending"]}} | \
    {"name": {"_contains": "John"}} | \
    {"_or": [{"gender": {"_eq": "MALE"}}, {"gender": {"_eq": "FEMALE"}}]} | \
    {"createdAt": {"_between": ["2023-01-01", "2023-12-31"]}}. \
    Sort example: [{"field":"id","sort":"DESC"},{"field":"name","sort":"ASC"}]\
    """)
  public String query_records(
    @ToolArg(description = "The project ID, e.g. 'dev_test', 'default'") String projectId,
    @ToolArg(description = "The model (entity) name to query records from, e.g. 'Student'") String modelName,
    @ToolArg(description = "Page number, starting from 1") int page,
    @ToolArg(description = "Number of records per page, e.g. 10, 20") int size,
    @ToolArg(description = """
      Filter condition as JSON string using the Filter DSL. \
      Examples: {"name": {"_eq": "John"}} | {"age": {"_gte": 18, "_lt": 65}} | \
      {"_or": [{"status": "active"}, {"status": "pending"}]}. \
      Pass empty string or null for no filter.\
      """) String filter,
    @ToolArg(description = """
      Sort condition as JSON array of {field, sort} objects. \
      Example: [{"field":"id","sort":"DESC"}]. Pass empty string or null for no sort.\
      """) String sort
  ) {
    log.infof("query_records called, projectId=%s, modelName=%s, page=%d, size=%d, filter=%s, sort=%s",
      projectId, modelName, page, size, filter, sort);
    try {
      String filterParam = (filter == null || filter.isBlank()) ? null : filter;
      String sortParam = (sort == null || sort.isBlank()) ? null : sort;
      PageDTO<Map<String, Object>> result = dataService.findPagingRecords(
        projectId, modelName, page, size, filterParam, sortParam, false
      );
      return JsonUtils.toJsonString(result);
    } catch (Exception e) {
      log.errorf(e, "query_records failed, projectId=%s, modelName=%s", projectId, modelName);
      return "Error: query_records failed - " + e.getMessage();
    }
  }

  @Tool(description = """
    Get a single record by its primary key ID from a model. \
    Returns the full record as a JSON object with all field values, or null if not found.\
    """)
  public String get_record(
    @ToolArg(description = "The project ID, e.g. 'dev_test', 'default'") String projectId,
    @ToolArg(description = "The model (entity) name, e.g. 'Student'") String modelName,
    @ToolArg(description = "The primary key ID of the record to retrieve") String recordId
  ) {
    log.infof("get_record called, projectId=%s, modelName=%s, recordId=%s", projectId, modelName, recordId);
    try {
      Map<String, Object> record = dataService.findOneRecord(projectId, modelName, recordId, false);
      if (record == null) {
        return "Error: Record not found: " + recordId;
      }
      return JsonUtils.toJsonString(record);
    } catch (Exception e) {
      log.errorf(e, "get_record failed, projectId=%s, modelName=%s, recordId=%s", projectId, modelName, recordId);
      return "Error: get_record failed - " + e.getMessage();
    }
  }

  @Tool(description = """
    Create a new record in a model (entity). The record data is a JSON object string \
    with field name-value pairs matching the entity's field definitions. \
    Identity fields (primary keys) with auto-increment can be omitted. \
    Enum fields use the enum element value (e.g. 'MALE'). \
    DateTime/Date fields use string format. \
    Example: {"studentName": "Alice", "age": 20, "gender": "FEMALE"}\
    """)
  public String create_record(
    @ToolArg(description = "The project ID, e.g. 'dev_test', 'default'") String projectId,
    @ToolArg(description = "The model (entity) name to create a record in, e.g. 'Student'") String modelName,
    @ToolArg(description = """
      Record data as JSON object string: {"fieldName": value, ...}. \
      Field names and value types must match the entity's field definitions.\
      """) String recordJson
  ) {
    log.infof("create_record called, projectId=%s, modelName=%s, recordJson=%s", projectId, modelName, recordJson);
    try {
      Map<String, Object> data = JsonUtils.parseToObject(recordJson, Map.class);
      Map<String, Object> created = dataService.createRecord(projectId, modelName, data);
      return "Record created: " + JsonUtils.toJsonString(created);
    } catch (Exception e) {
      log.errorf(e, "create_record failed, projectId=%s, modelName=%s, recordJson=%s", projectId, modelName, recordJson);
      return "Error: create_record failed - " + e.getMessage();
    }
  }

  @Tool(description = """
    Update an existing record by its primary key ID. \
    Only the provided fields will be updated (partial update / PATCH semantics). \
    Omitted fields remain unchanged. Provide the updated field data as a JSON object string.\
    """)
  public String update_record(
    @ToolArg(description = "The project ID, e.g. 'dev_test', 'default'") String projectId,
    @ToolArg(description = "The model (entity) name, e.g. 'Student'") String modelName,
    @ToolArg(description = "The primary key ID of the record to update") String recordId,
    @ToolArg(description = """
      Updated field data as JSON object string: {"fieldName": newValue, ...}. \
      Only included fields are updated.\
      """) String recordJson
  ) {
    log.infof("update_record called, projectId=%s, modelName=%s, recordId=%s, recordJson=%s",
      projectId, modelName, recordId, recordJson);
    try {
      Map<String, Object> data = JsonUtils.parseToObject(recordJson, Map.class);
      Map<String, Object> updated = dataService.updateRecord(projectId, modelName, recordId, data);
      return "Record updated: " + JsonUtils.toJsonString(updated);
    } catch (Exception e) {
      log.errorf(e, "update_record failed, projectId=%s, modelName=%s, recordId=%s", projectId, modelName, recordId);
      return "Error: update_record failed - " + e.getMessage();
    }
  }

  @Tool(description = "Delete a record by its primary key ID from a model. This operation is irreversible.")
  public String delete_record(
    @ToolArg(description = "The project ID, e.g. 'dev_test', 'default'") String projectId,
    @ToolArg(description = "The model (entity) name, e.g. 'Student'") String modelName,
    @ToolArg(description = "The primary key ID of the record to delete") String recordId
  ) {
    log.infof("delete_record called, projectId=%s, modelName=%s, recordId=%s", projectId, modelName, recordId);
    try {
      dataService.deleteRecord(projectId, modelName, recordId);
      return "Record deleted: " + recordId;
    } catch (Exception e) {
      log.errorf(e, "delete_record failed, projectId=%s, modelName=%s, recordId=%s", projectId, modelName, recordId);
      return "Error: delete_record failed - " + e.getMessage();
    }
  }
}
