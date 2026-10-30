package dev.flexmodel.apps;

import dev.flexmodel.common.dto.PageDTO;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.Map;

/**
 * 应用表单记录 REST API。
 */
@Tag(name = "应用", description = "应用表单记录管理")
@Path("/apps/{appId}/forms/{formName}/records")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AppRecordResource {

  @Inject
  AppRecordService appRecordService;

  @Operation(summary = "获取表单记录分页列表")
  @GET
  public PageDTO<Map<String, Object>> list(
    @PathParam("appId") String appId,
    @PathParam("formName") String formName,
    @QueryParam("page") @DefaultValue("1") int page,
    @QueryParam("size") @DefaultValue("20") int size,
    @QueryParam("search") String search,
    @QueryParam("sortField") String sortField,
    @QueryParam("sortOrder") String sortOrder,
    @QueryParam("viewTabId") String viewTabId,
    @QueryParam("filter") String filter,
    @QueryParam("mainFormName") String mainFormName,
    @QueryParam("mainRecordId") String mainRecordId,
    @QueryParam("referenceField") String referenceField
  ) {
    return appRecordService.findRecords(
      appId, formName, page, size, search, sortField, sortOrder, viewTabId,
      mainFormName, mainRecordId, referenceField, filter);
  }

  @Parameter(name = "recordId", description = "记录ID", in = ParameterIn.PATH, required = true)
  @Operation(summary = "获取表单记录详情")
  @GET
  @Path("/{recordId}")
  public Map<String, Object> get(
    @PathParam("appId") String appId,
    @PathParam("formName") String formName,
    @PathParam("recordId") String recordId,
    @QueryParam("mainFormName") String mainFormName,
    @QueryParam("mainRecordId") String mainRecordId,
    @QueryParam("referenceField") String referenceField
  ) {
    return appRecordService.findRecord(
      appId, formName, recordId, mainFormName, mainRecordId, referenceField);
  }

  @Operation(summary = "创建表单记录")
  @POST
  public Response create(
    @PathParam("appId") String appId,
    @PathParam("formName") String formName,
    Map<String, Object> record,
    @QueryParam("mainFormName") String mainFormName,
    @QueryParam("mainRecordId") String mainRecordId,
    @QueryParam("referenceField") String referenceField
  ) {
    return Response.status(Response.Status.CREATED)
      .entity(appRecordService.createRecord(
        appId, formName, record, mainFormName, mainRecordId, referenceField))
      .build();
  }

  @Parameter(name = "recordId", description = "记录ID", in = ParameterIn.PATH, required = true)
  @Operation(summary = "更新表单记录")
  @PUT
  @Path("/{recordId}")
  public Map<String, Object> update(
    @PathParam("appId") String appId,
    @PathParam("formName") String formName,
    @PathParam("recordId") String recordId,
    Map<String, Object> record,
    @QueryParam("mainFormName") String mainFormName,
    @QueryParam("mainRecordId") String mainRecordId,
    @QueryParam("referenceField") String referenceField
  ) {
    return appRecordService.updateRecord(
      appId, formName, recordId, record, mainFormName, mainRecordId, referenceField);
  }

  @Parameter(name = "recordId", description = "记录ID", in = ParameterIn.PATH, required = true)
  @Operation(summary = "删除表单记录")
  @DELETE
  @Path("/{recordId}")
  public Response delete(
    @PathParam("appId") String appId,
    @PathParam("formName") String formName,
    @PathParam("recordId") String recordId,
    @QueryParam("mainFormName") String mainFormName,
    @QueryParam("mainRecordId") String mainRecordId,
    @QueryParam("referenceField") String referenceField
  ) {
    appRecordService.deleteRecord(
      appId, formName, recordId, mainFormName, mainRecordId, referenceField);
    return Response.noContent().build();
  }
}
