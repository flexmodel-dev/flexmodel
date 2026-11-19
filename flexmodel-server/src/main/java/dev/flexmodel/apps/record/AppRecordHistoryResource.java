package dev.flexmodel.apps.record;

import dev.flexmodel.apps.record.dto.AppRecordHistoryDetail;
import dev.flexmodel.apps.record.dto.AppRecordHistoryPage;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * 应用表单记录历史 REST API。只提供查询，不提供创建、更新、删除或恢复。
 */
@Tag(name = "应用", description = "记录历史")
@Path("/apps/{appId}/forms/{formName}/records/{recordId}/histories")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AppRecordHistoryResource {

  @Inject
  AppRecordHistoryService appRecordHistoryService;

  @Operation(summary = "获取记录历史列表")
  @GET
  public AppRecordHistoryPage list(
    @PathParam("appId") String appId,
    @PathParam("formName") String formName,
    @PathParam("recordId") String recordId,
    @QueryParam("size") @DefaultValue("20") int size,
    @QueryParam("cursor") String cursor
  ) {
    return appRecordHistoryService.page(appId, formName, recordId, size, cursor);
  }

  @Operation(summary = "获取记录历史详情")
  @GET
  @Path("/{eventId}")
  public AppRecordHistoryDetail detail(
    @PathParam("appId") String appId,
    @PathParam("formName") String formName,
    @PathParam("recordId") String recordId,
    @PathParam("eventId") String eventId
  ) {
    return appRecordHistoryService.detail(appId, formName, recordId, eventId);
  }
}
