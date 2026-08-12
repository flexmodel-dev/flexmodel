package dev.flexmodel.open;

import dev.flexmodel.common.SessionContext;
import dev.flexmodel.common.authz.PermissionHelper;
import dev.flexmodel.common.dto.PageDTO;
import dev.flexmodel.data.DataService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Open API — 数据记录 CRUD。
 * <p>
 * 路径前缀 {@code /open/{projectId}/models/{modelName}/records}，
 * 面向终端用户（IdP 用户 / open scope API Key）。
 * 管理操作（建模型、改 schema）不在 open 路由。
 *
 * @author cjbi
 */
@ApplicationScoped
@Path("/open/{projectId}/models/{modelName}/records")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OpenDataResource {

  private static final int MAX_BATCH_SIZE = 200;

  @Inject
  DataService dataService;

  @Inject
  SessionContext sessionContext;

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

  @POST
  public Map<String, Object> createRecord(
    @PathParam("projectId") String projectId,
    @PathParam("modelName") String modelName,
    Map<String, Object> record
  ) {
    requirePermission("data:" + modelName + ":create");
    return dataService.createRecord(projectId, modelName, record);
  }

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

  private void requirePermission(String permission) {
    Set<String> permissions = sessionContext.getPermissions();
    if (permissions == null) {
      return;
    }
    if (!PermissionHelper.hasPermission(permissions, permission)) {
      throw new ForbiddenException("Permission denied: " + permission);
    }
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
