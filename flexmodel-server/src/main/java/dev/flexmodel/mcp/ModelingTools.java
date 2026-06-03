package dev.flexmodel.mcp;

import dev.flexmodel.common.utils.JsonUtils;
import dev.flexmodel.model.*;
import dev.flexmodel.modeling.ModelingService;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;

/**
 * MCP 工具：数据建模
 * 提供模型列表、详情、创建实体/枚举、删除模型和同步 Schema 能力
 */
public class ModelingTools {

  private static final Logger log = Logger.getLogger(ModelingTools.class);

  @Inject
  ModelingService modelingService;

  private final JsonUtils json = JsonUtils.getInstance();

  @Tool(description = """
    List all models (entities, enums, native queries) in a project. \
    Returns a JSON array of model definitions. Each model has: type (entity/enum/native_query), name, \
    and type-specific fields. Entities include fields array and indexes array. \
    Enums include elements array. Native queries include statement string.\
    """)
  public String list_models(
    @ToolArg(description = "The project ID, e.g. 'dev_test', 'default'") String projectId
  ) {
    log.infof("list_models called, projectId=%s", projectId);
    try {
      List<SchemaObject> models = modelingService.findModels(projectId);
      return json.stringify(models);
    } catch (Exception e) {
      log.errorf(e, "list_models failed, projectId=%s", projectId);
      return "Error: list_models failed - " + e.getMessage();
    }
  }

  @Tool(description = """
    Get the detailed definition of a specific model (entity, enum, or native query). \
    For entities: returns fields array (each with name, type, nullable, unique, etc.) and indexes array. \
    For enums: returns elements array. For native queries: returns the SQL statement.\
    """)
  public String get_model(
    @ToolArg(description = "The project ID, e.g. 'dev_test', 'default'") String projectId,
    @ToolArg(description = "The model name to retrieve, e.g. 'Student', 'UserGender'") String modelName
  ) {
    log.infof("get_model called, projectId=%s, modelName=%s", projectId, modelName);
    try {
      SchemaObject model = modelingService.findModel(projectId, modelName);
      return json.stringify(model);
    } catch (Exception e) {
      log.errorf(e, "get_model failed, projectId=%s, modelName=%s", projectId, modelName);
      return "Error: get_model failed - " + e.getMessage();
    }
  }

  @Tool(description = """
    Create a new entity model (maps to a database table) in a project. \
    The entityJson must be a JSON object with type='entity', name, and fields array. \
    Supported field types: String (with length, default 255), Int, Long, Float (with precision/scale), \
    Boolean, DateTime, Date, Time, JSON, Enum (EnumRef, with 'from' pointing to an enum name, 'multiple' for array), \
    Relation (with 'from', 'localField', 'foreignField', 'cascadeDelete'). \
    Common field properties: name, type, modelName (must match entity name), nullable (default true), \
    unique (default false), identity (for primary key), comment, defaultValue. \
    Optional indexes array at entity level. \
    Example: {"type":"entity","name":"Student","fields":[\
    {"name":"id","type":"Long","identity":true,"modelName":"Student"},\
    {"name":"studentName","type":"String","modelName":"Student","length":255},\
    {"name":"age","type":"Int","modelName":"Student"},\
    {"name":"gender","type":"EnumRef","from":"UserGender","multiple":false,"modelName":"Student"}]}\
    """)
  public String create_entity_model(
    @ToolArg(description = "The project ID, e.g. 'dev_test', 'default'") String projectId,
    @ToolArg(description = """
      Entity definition as JSON string: must contain type='entity', name, and fields array. \
      Each field must have name, type, and modelName. See tool description for supported types and properties.\
      """) String entityJson
  ) {
    log.infof("create_entity_model called, projectId=%s, entityJson=%s", projectId, entityJson);
    try {
      SchemaObject model = json.parseToObject(entityJson, SchemaObject.class);
      SchemaObject created = modelingService.createModel(projectId, model);
      return "Entity model created: " + json.stringify(created);
    } catch (Exception e) {
      log.errorf(e, "create_entity_model failed, projectId=%s, entityJson=%s", projectId, entityJson);
      return "Error: create_entity_model failed - " + e.getMessage();
    }
  }

  @Tool(description = """
    Create a new enum definition in a project. Enums define a fixed set of string values \
    that can be referenced by entity fields via the EnumRef field type (using 'from' property). \
    The enumJson must be a JSON object with name, type='enum', and elements (string array). \
    Elements should be uppercase or UPPER_SNAKE_CASE by convention. \
    Optional: comment for description, additionalProperties for i18n or extensions. \
    Example: {"name":"UserGender","type":"enum","elements":["UNKNOWN","MALE","FEMALE"],"comment":"User gender options"}\
    """)
  public String create_enum_model(
    @ToolArg(description = "The project ID, e.g. 'dev_test', 'default'") String projectId,
    @ToolArg(description = "Enum definition as JSON string: must contain name, type='enum', and elements (string array)") String enumJson
  ) {
    log.infof("create_enum_model called, projectId=%s, enumJson=%s", projectId, enumJson);
    try {
      SchemaObject model = json.parseToObject(enumJson, SchemaObject.class);
      SchemaObject created = modelingService.createModel(projectId, model);
      return "Enum model created: " + json.stringify(created);
    } catch (Exception e) {
      log.errorf(e, "create_enum_model failed, projectId=%s, enumJson=%s", projectId, enumJson);
      return "Error: create_enum_model failed - " + e.getMessage();
    }
  }

  @Tool(description = """
    Delete a model (entity, enum, or native query) from a project. \
    This will remove the model definition and drop the corresponding database table/object if it exists. \
    Use with caution as this operation is destructive.\
    """)
  public String delete_model(
    @ToolArg(description = "The project ID, e.g. 'dev_test', 'default'") String projectId,
    @ToolArg(description = "The model name to delete, e.g. 'Student', 'UserGender'") String modelName
  ) {
    log.infof("delete_model called, projectId=%s, modelName=%s", projectId, modelName);
    try {
      modelingService.dropModel(projectId, modelName);
      return "Model deleted: " + modelName;
    } catch (Exception e) {
      log.errorf(e, "delete_model failed, projectId=%s, modelName=%s", projectId, modelName);
      return "Error: delete_model failed - " + e.getMessage();
    }
  }

//   @Tool(description = """
//     Synchronize model definitions to the database by generating and executing DDL statements \
//     (CREATE TABLE, ALTER TABLE, etc.). This materializes the model schema into physical database objects. \
//     Provide a comma-separated list of model names to sync specific models, or pass empty/null to sync all. \
//     Use with caution as this modifies the database schema directly.\
//     """)
//   public String sync_schema(
//     @ToolArg(description = "The project ID, e.g. 'dev_test', 'default'") String projectId,
//     @ToolArg(description = "Comma-separated model names to sync, e.g. 'Student,Teacher'. Pass empty string or null to sync all models.") String modelNames
//   ) {
//     Set<String> names = null;
//     if (modelNames != null && !modelNames.isBlank()) {
//       names = Set.of(modelNames.split(","));
//     }
//     List<SchemaObject> result = modelingService.syncModels(projectId, names);
//     return "Schema synced. Affected models: " + json.stringify(result);
//   }
}
