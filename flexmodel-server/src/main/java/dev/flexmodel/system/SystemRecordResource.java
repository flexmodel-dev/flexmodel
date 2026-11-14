package dev.flexmodel.system;

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
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.Map;

@Tag(name = "系统表单", description = "系统内置表单记录")
@Path("/system/forms/{formName}/records")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SystemRecordResource {

  @Inject
  SystemRecordService systemRecordService;

  @Operation(summary = "分页查询系统表单记录")
  @GET
  public PageDTO<Map<String, Object>> list(
    @PathParam("formName") String formName,
    @QueryParam("page") @DefaultValue("1") int page,
    @QueryParam("size") @DefaultValue("20") int size,
    @QueryParam("search") String search,
    @QueryParam("sortField") String sortField,
    @QueryParam("sortOrder") String sortOrder,
    @QueryParam("departmentId") String departmentId,
    @QueryParam("includeDescendants") @DefaultValue("false") boolean includeDescendants
  ) {
    return systemRecordService.findRecords(
      formName, page, size, search, sortField, sortOrder, departmentId, includeDescendants);
  }

  @Operation(summary = "查询系统表单记录详情")
  @GET
  @Path("/{recordId}")
  public Map<String, Object> get(
    @PathParam("formName") String formName,
    @PathParam("recordId") String recordId
  ) {
    return systemRecordService.findRecord(formName, recordId);
  }

  @Operation(summary = "创建系统表单记录")
  @POST
  public Response create(
    @PathParam("formName") String formName,
    Map<String, Object> record
  ) {
    return Response.status(Response.Status.CREATED)
      .entity(systemRecordService.createRecord(formName, record))
      .build();
  }

  @Operation(summary = "更新系统表单记录")
  @PUT
  @Path("/{recordId}")
  public Map<String, Object> update(
    @PathParam("formName") String formName,
    @PathParam("recordId") String recordId,
    Map<String, Object> record
  ) {
    return systemRecordService.updateRecord(formName, recordId, record);
  }

  @Operation(summary = "删除系统表单记录")
  @DELETE
  @Path("/{recordId}")
  public Response delete(
    @PathParam("formName") String formName,
    @PathParam("recordId") String recordId
  ) {
    systemRecordService.deleteRecord(formName, recordId);
    return Response.noContent().build();
  }
}
