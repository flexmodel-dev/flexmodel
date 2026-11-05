package dev.flexmodel.mcp;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.common.dto.PageDTO;
import dev.flexmodel.data.DataService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * MCP 工具：数据操作
 * 提供记录的分页查询、单条查询、创建、更新和删除能力
 * <p>
 * 与 {@link AppTools} 相同，注解使用 langchain4j 定义，MCP Server 自动注册复用。
 * 访问控制由 {@link McpToolAccess} 统一处理。
 */
@Slf4j
@ApplicationScoped
public class DataTools {

  @Inject
  DataService dataService;

  @Inject
  McpToolAccess access;

  @Tool(name = "query_records", value = """
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
    @P("The project ID, e.g. 'dev_test', 'default'") String projectId,
    @P("The model (entity) name to query records from, e.g. 'Student'") String modelName,
    @P("Page number, starting from 1") int page,
    @P("Number of records per page, e.g. 10, 20") int size,
    @P(value = """
      Filter condition as JSON string using the Filter DSL. \
      Examples: {"name": {"_eq": "John"}} | {"age": {"_gte": 18, "_lt": 65}} | \
      {"_or": [{"status": "active"}, {"status": "pending"}]}. \
      Pass empty string or null for no filter.\
      """, required = false) String filter,
    @P(value = """
      Sort condition as JSON array of {field, sort} objects. \
      Example: [{"field":"id","sort":"DESC"}]. Pass empty string or null for no sort.\
      """, required = false) String sort
  ) {
    log.info("query_records called, projectId={}, modelName={}, page={}, size={}, filter={}, sort={}",
      projectId, modelName, page, size, filter, sort);
    try {
      access.requireDirectory();
      String filterParam = (filter == null || filter.isBlank()) ? null : filter;
      String sortParam = (sort == null || sort.isBlank()) ? null : sort;
      PageDTO<Map<String, Object>> result = dataService.findPagingRecords(
        projectId, modelName, page, size, filterParam, sortParam, null
      );
      return JsonUtils.toJsonString(result);
    } catch (Exception e) {
      log.error("query_records failed, projectId={}, modelName={}", projectId, modelName, e);
      return "Error: query_records failed - " + e.getMessage();
    }
  }

  @Tool(name = "get_record", value = """
    Get a single record by its primary key ID from a model. \
    Returns the full record as a JSON object with all field values, or null if not found.\
    """)
  public String get_record(
    @P("The project ID, e.g. 'dev_test', 'default'") String projectId,
    @P("The model (entity) name, e.g. 'Student'") String modelName,
    @P("The primary key ID of the record to retrieve") String recordId
  ) {
    log.info("get_record called, projectId={}, modelName={}, recordId={}", projectId, modelName, recordId);
    try {
      access.requireDirectory();
      Map<String, Object> record = dataService.findOneRecord(projectId, modelName, recordId, null);
      if (record == null) {
        return "Error: Record not found: " + recordId;
      }
      return JsonUtils.toJsonString(record);
    } catch (Exception e) {
      log.error("get_record failed, projectId={}, modelName={}, recordId={}", projectId, modelName, recordId, e);
      return "Error: get_record failed - " + e.getMessage();
    }
  }

  @Tool(name = "create_record", value = """
    Create a new record in a model (entity). The record data is a JSON object string \
    with field name-value pairs matching the entity's field definitions. \
    Identity fields (primary keys) with auto-increment can be omitted. \
    Enum fields use the enum element value (e.g. 'MALE'). \
    DateTime/Date fields use string format. \
    Example: {"studentName": "Alice", "age": 20, "gender": "FEMALE"}\
    """)
  public String create_record(
    @P("The project ID, e.g. 'dev_test', 'default'") String projectId,
    @P("The model (entity) name to create a record in, e.g. 'Student'") String modelName,
    @P("""
      Record data as JSON object string: {"fieldName": value, ...}. \
      Field names and value types must match the entity's field definitions.\
      """) String recordJson
  ) {
    log.info("create_record called, projectId={}, modelName={}, recordJson={}", projectId, modelName, recordJson);
    try {
      access.requireDirectory();
      Map<String, Object> data = JsonUtils.parseToObject(recordJson, Map.class);
      Map<String, Object> created = dataService.createRecord(projectId, modelName, data);
      return "Record created: " + JsonUtils.toJsonString(created);
    } catch (Exception e) {
      log.error("create_record failed, projectId={}, modelName={}, recordJson={}", projectId, modelName, recordJson, e);
      return "Error: create_record failed - " + e.getMessage();
    }
  }

  @Tool(name = "update_record", value = """
    Update an existing record by its primary key ID. \
    Only the provided fields will be updated (partial update / PATCH semantics). \
    Omitted fields remain unchanged. Provide the updated field data as a JSON object string.\
    """)
  public String update_record(
    @P("The project ID, e.g. 'dev_test', 'default'") String projectId,
    @P("The model (entity) name, e.g. 'Student'") String modelName,
    @P("The primary key ID of the record to update") String recordId,
    @P("""
      Updated field data as JSON object string: {"fieldName": newValue, ...}. \
      Only included fields are updated.\
      """) String recordJson
  ) {
    log.info("update_record called, projectId={}, modelName={}, recordId={}, recordJson={}",
      projectId, modelName, recordId, recordJson);
    try {
      access.requireDirectory();
      Map<String, Object> data = JsonUtils.parseToObject(recordJson, Map.class);
      Map<String, Object> updated = dataService.updateRecord(projectId, modelName, recordId, data);
      return "Record updated: " + JsonUtils.toJsonString(updated);
    } catch (Exception e) {
      log.error("update_record failed, projectId={}, modelName={}, recordId={}", projectId, modelName, recordId, e);
      return "Error: update_record failed - " + e.getMessage();
    }
  }

  @Tool(name = "delete_record", value = "Delete a record by its primary key ID from a model. This operation is irreversible.")
  public String delete_record(
    @P("The project ID, e.g. 'dev_test', 'default'") String projectId,
    @P("The model (entity) name, e.g. 'Student'") String modelName,
    @P("The primary key ID of the record to delete") String recordId
  ) {
    log.info("delete_record called, projectId={}, modelName={}, recordId={}", projectId, modelName, recordId);
    try {
      access.requireDirectory();
      dataService.deleteRecord(projectId, modelName, recordId);
      return "Record deleted: " + recordId;
    } catch (Exception e) {
      log.error("delete_record failed, projectId={}, modelName={}, recordId={}", projectId, modelName, recordId, e);
      return "Error: delete_record failed - " + e.getMessage();
    }
  }
}
