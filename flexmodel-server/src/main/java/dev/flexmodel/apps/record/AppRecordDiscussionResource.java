package dev.flexmodel.apps.record;

import dev.flexmodel.apps.record.dto.AppRecordDiscussionCreateRequest;
import dev.flexmodel.apps.record.dto.AppRecordDiscussionPage;
import dev.flexmodel.apps.record.dto.AppRecordDiscussionView;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * 应用表单记录讨论 REST API。
 */
@Tag(name = "应用", description = "记录讨论")
@Path("/apps/{appId}/forms/{formName}/records/{recordId}/discussions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AppRecordDiscussionResource {

  @Inject
  AppRecordDiscussionService appRecordDiscussionService;

  @Operation(summary = "获取记录讨论列表")
  @GET
  public AppRecordDiscussionPage list(
    @PathParam("appId") String appId,
    @PathParam("formName") String formName,
    @PathParam("recordId") String recordId,
    @QueryParam("page") @DefaultValue("1") int page,
    @QueryParam("size") @DefaultValue("20") int size
  ) {
    return appRecordDiscussionService.page(appId, formName, recordId, page, size);
  }

  @Operation(summary = "发表记录讨论")
  @POST
  public Response create(
    @PathParam("appId") String appId,
    @PathParam("formName") String formName,
    @PathParam("recordId") String recordId,
    AppRecordDiscussionCreateRequest request
  ) {
    AppRecordDiscussionView discussion = appRecordDiscussionService.create(appId, formName, recordId, request);
    return Response.status(Response.Status.CREATED).entity(discussion).build();
  }

  @Operation(summary = "删除记录讨论")
  @DELETE
  @Path("/{discussionId}")
  public Response delete(
    @PathParam("appId") String appId,
    @PathParam("formName") String formName,
    @PathParam("recordId") String recordId,
    @PathParam("discussionId") String discussionId
  ) {
    appRecordDiscussionService.delete(appId, formName, recordId, discussionId);
    return Response.noContent().build();
  }
}
