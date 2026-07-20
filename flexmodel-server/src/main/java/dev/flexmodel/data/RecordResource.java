package dev.flexmodel.data;

import dev.flexmodel.common.SessionContext;
import dev.flexmodel.common.authz.PermissionHelper;
import dev.flexmodel.common.dto.PageDTO;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.media.SchemaProperty;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author cjbi
 */
@Tag(name = "记录", description = "模型数据记录管理")
@Path("/projects/{projectId}/models/{modelName}/records")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RecordResource {

  private static final int MAX_BATCH_SIZE = 200;

  @Inject
  DataService dataService;

  @Inject
  SessionContext sessionContext;

  private void requirePermission(String permission) {
    Set<String> permissions = sessionContext.getPermissions();
    if (permissions == null) {
      return;
    }
    if (!PermissionHelper.hasPermission(permissions, permission)) {
      throw new ForbiddenException("Permission denied: " + permission);
    }
  }

  @APIResponse(
    name = "200",
    responseCode = "200",
    description = "OK",
    content = {@Content(
      mediaType = "application/json",
      schema = @Schema(
        properties = {
          @SchemaProperty(name = "total", description = "总数"),
          @SchemaProperty(name = "list", description = "日志列表", type = SchemaType.ARRAY, implementation = Map.class)
        }
      )
    )
    })
  @Parameter(name = "page", description = "当前页，默认值：1", examples = {@ExampleObject(value = "1")}, in = ParameterIn.QUERY)
  @Parameter(name = "size", description = "第几页，默认值：15", examples = {@ExampleObject(value = "15")}, in = ParameterIn.QUERY)
  @Parameter(
    name = "filter", description = "查询条件，更多信息见查询条件文档",
    examples = {@ExampleObject(value = """
      "{ \"username\": { \"_eq\": \"john_doe\" } }"
      """)},
    in = ParameterIn.QUERY)
  @Parameter(name = "expand", description = "要展开的关联字段列表，逗号分隔，例如 classId,courseIds。支持嵌套展开，例如 classId.teacher。不传则不加载关联数据", examples = {@ExampleObject(value = "classId,courseIds")}, in = ParameterIn.QUERY)
  @Parameter(name = "sort", description = "排序", examples = {@ExampleObject(value = """
    "[{\"field\":\"name\",\"sort\":\"ASC\"}, {\"field\":\"id\",\"sort\":\"DESC\"}]"
    """)}, in = ParameterIn.QUERY)
  @Operation(summary = "获取模型数据记录列表")
  @GET
  public PageDTO<Map<String, Object>> findPagingRecords(
    @PathParam("projectId") String projectId,
    @PathParam("modelName") String modelName,
    @QueryParam("page") @DefaultValue("1") int page,
    @QueryParam("size") @DefaultValue("15") int size,
    @QueryParam("filter") String filter,
    @QueryParam("expand") List<String> expand,
    @QueryParam("sort") String sort
  ) {
    requirePermission("data:" + modelName + ":view");
    return dataService.findPagingRecords(projectId, modelName, page, size, filter, sort, expand);
  }

  @Parameter(name = "id", description = "ID", examples = {@ExampleObject(value = "1")}, in = ParameterIn.PATH)
  @Operation(summary = "获取单条模型数据记录")
  @GET
  @Path("/{id}")
  public Map<String, Object> findOneRecord(
    @PathParam("projectId") String projectId,
    @PathParam("modelName") String modelName,
    @PathParam("id") String id,
    @QueryParam("expand") List<String> expand
  ) {
    requirePermission("data:" + modelName + ":view");
    return dataService.findOneRecord(projectId, modelName, id, expand);
  }


  @RequestBody(
    name = "请求体",
    content = {@Content(
      mediaType = "application/json",
      examples = {
        @ExampleObject(name = "请求示例，可包含关联数据，关联数据有Id字段则更新这条关联记录", value = """
            {
            "studentName": "张三",
            "gender": "MALE",
            "interest": ["chang", "tiao", "rap", "daLanQiu"],
            "age": 10,
            "classId": 1,
            "studentDetail": {
              "description": "张三的描述"
            },
            "courses": [
               {
                 "courseNo":"Math",
                 "courseName":"数学"
               },
               {
                 "courseNo":"YuWen",
                 "courseName":"语文"
               },
               {
                 "courseNo":"Eng",
                 "courseName":"英语"
               }
            ]
          }
          """)
      }
    )}
  )
  @Operation(summary = "创建模型数据记录")
  @POST
  public Map<String, Object> createRecord(
    @PathParam("projectId") String projectId,
    @PathParam("modelName") String modelName,
    Map<String, Object> record
  ) {
    requirePermission("data:" + modelName + ":create");
    return dataService.createRecord(projectId, modelName, record);
  }

  @RequestBody(
    name = "请求体",
    content = {@Content(
      mediaType = "application/json",
      examples = {
        @ExampleObject(name = "请求示例，可包含关联数据，关联数据有Id字段则更新这条关联记录", value = """
            {
            "id": 1,
            "studentName": "张三",
            "gender": "MALE",
            "interest": ["chang", "tiao", "rap", "daLanQiu"],
            "age": 10,
            "classId": 1,
            "studentDetail": {
              "description": "张三的描述"
            },
            "courses": [
               {
                 "courseNo":"Math",
                 "courseName":"数学"
               },
               {
                 "courseNo":"YuWen",
                 "courseName":"语文"
               },
               {
                 "courseNo":"Eng",
                 "courseName":"英语"
               }
            ]
          }
          """)
      }
    )}
  )
  @Parameter(name = "id", description = "ID", examples = {@ExampleObject(value = "1")}, in = ParameterIn.PATH)
  @Operation(summary = "更新模型数据记录")
  @PUT
  @Path("/{id}")
  public Map<String, Object> updateRecord(
    @PathParam("projectId") String projectId,
    @PathParam("modelName") String modelName,
    @PathParam("id") String id,
    Map<String, Object> record
  ) {
    requirePermission("data:" + modelName + ":update");
    return dataService.updateRecord(projectId, modelName, id, record);
  }

  @RequestBody(
    name = "请求体",
    content = {@Content(
      mediaType = "application/json",
      examples = {
        @ExampleObject(name = "请求示例，可包含关联数据，关联数据有Id字段则更新这条关联记录", value = """
            {
            "studentName": "张三",
            "gender": "MALE",
            "interest": ["chang", "tiao", "rap", "daLanQiu"],
            "age": 10,
            "classId": 1,
            "studentDetail": {
              "description": "张三的描述"
            },
            "courses": [
               {
                 "courseNo":"Math",
                 "courseName":"数学"
               },
               {
                 "courseNo":"YuWen",
                 "courseName":"语文"
               },
               {
                 "courseNo":"Eng",
                 "courseName":"英语"
               }
            ]
          }
          """)
      }
    )}
  )
  @Parameter(name = "id", description = "ID", examples = {@ExampleObject(value = "1")}, in = ParameterIn.PATH)
  @Operation(summary = "更新模型数据记录(局部更新)")
  @PATCH
  @Path("/{id}")
  public Map<String, Object> updateRecordIgnoreNull(
    @PathParam("projectId") String projectId,
    @PathParam("modelName") String modelName,
    @PathParam("id") String id,
    Map<String, Object> record
  ) {
    requirePermission("data:" + modelName + ":update");
    return dataService.updateRecordIgnoreNull(projectId, modelName, id, record);
  }

  @Parameter(name = "id", description = "ID", examples = {@ExampleObject(value = "1")}, in = ParameterIn.PATH)
  @Operation(summary = "删除模型数据记录")
  @DELETE
  @Path("/{id}")
  public void deleteRecord(
    @PathParam("projectId") String projectId,
    @PathParam("modelName") String modelName,
    @PathParam("id") String id
  ) {
    requirePermission("data:" + modelName + ":delete");
    dataService.deleteRecord(projectId, modelName, id);
  }

  @RequestBody(
    name = "请求体",
    content = {@Content(
      mediaType = "application/json",
      examples = {
        @ExampleObject(name = "批量创建请求示例", value = """
            [
              {"studentName": "张三", "gender": "MALE", "age": 10, "classId": 1},
              {"studentName": "李四", "gender": "FEMALE", "age": 11, "classId": 2}
            ]
          """)
      }
    )}
  )
  @Operation(summary = "批量创建模型数据记录")
  @POST
  @Path("/batch")
  public List<Map<String, Object>> createRecords(
    @PathParam("projectId") String projectId,
    @PathParam("modelName") String modelName,
    List<Map<String, Object>> records
  ) {
    validateBatchSize(records);
    requirePermission("data:" + modelName + ":create");
    return dataService.createRecords(projectId, modelName, records);
  }

  @RequestBody(
    name = "请求体",
    content = {@Content(
      mediaType = "application/json",
      examples = {
        @ExampleObject(name = "批量更新请求示例，每条记录必须包含 id 字段", value = """
            [
              {"id": 1, "studentName": "张三 Updated", "age": 12},
              {"id": 2, "studentName": "李四 Updated", "classId": 3}
            ]
          """)
      }
    )}
  )
  @Operation(summary = "批量更新模型数据记录")
  @PUT
  @Path("/batch")
  public List<Map<String, Object>> updateRecords(
    @PathParam("projectId") String projectId,
    @PathParam("modelName") String modelName,
    List<Map<String, Object>> records
  ) {
    validateBatchSize(records);
    requirePermission("data:" + modelName + ":update");
    return dataService.updateRecords(projectId, modelName, records);
  }

  @RequestBody(
    name = "请求体",
    content = {@Content(
      mediaType = "application/json",
      examples = {
        @ExampleObject(name = "批量删除请求示例，传入要删除的 ID 列表", value = """
            ["1", "2", "3"]
          """)
      }
    )}
  )
  @Operation(summary = "批量删除模型数据记录")
  @DELETE
  @Path("/batch")
  public long deleteRecords(
    @PathParam("projectId") String projectId,
    @PathParam("modelName") String modelName,
    List<String> ids
  ) {
    validateBatchSize(ids);
    requirePermission("data:" + modelName + ":delete");
    return dataService.deleteRecords(projectId, modelName, new ArrayList<>(ids));
  }

  private <T> void validateBatchSize(List<T> items) {
    if (items == null || items.isEmpty()) {
      throw new BadRequestException("请求体不能为空");
    }
    if (items.size() > MAX_BATCH_SIZE) {
      throw new BadRequestException("批量操作记录数不能超过 " + MAX_BATCH_SIZE);
    }
  }

}
