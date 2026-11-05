package dev.flexmodel.apps.approval;

import dev.flexmodel.apps.approval.dto.*;
import dev.flexmodel.common.authz.RequiresPermissions;
import dev.flexmodel.directory.DirectoryService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;

@Path("/apps/{appId}")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AppApprovalFlowResource {
  @Inject
  AppApprovalFlowService appApprovalFlowService;
  @Inject
  AppApprovalRuntime runtime;
  @Inject
  DirectoryService directoryService;
  @Inject
  AppApprovalService approvalsService;

  @GET
  @Path("/approval-flows")
  @RequiresPermissions("flow:view")
  public List<AppApprovalFlowResponse> list(@PathParam("appId") String appId) {
    return appApprovalFlowService.list(appId).stream().map(AppApprovalFlowModel::encode)
      .map(AppApprovalFlowResponse::from).toList();
  }

  @GET
  @Path("/approval-flows/{id}")
  @RequiresPermissions("flow:view")
  public AppApprovalFlowResponse get(@PathParam("appId") String appId, @PathParam("id") String id) {
    return AppApprovalFlowResponse.from(AppApprovalFlowModel.encode(appApprovalFlowService.get(appId, id)));
  }

  @POST
  @Path("/approval-flows")
  @RequiresPermissions("flow:create")
  public Response create(@PathParam("appId") String appId, AppApprovalFlowRequest body) {
    return Response.status(201).entity(AppApprovalFlowResponse.from(
      AppApprovalFlowModel.encode(appApprovalFlowService.save(appId, null, AppApprovalFlowModel.decode(body.toMap()))))).build();
  }

  @PUT
  @Path("/approval-flows/{id}")
  @RequiresPermissions("flow:update")
  public AppApprovalFlowResponse save(@PathParam("appId") String appId, @PathParam("id") String id, AppApprovalFlowRequest body) {
    return AppApprovalFlowResponse.from(AppApprovalFlowModel.encode(
      appApprovalFlowService.save(appId, id, AppApprovalFlowModel.decode(body.toMap()))));
  }

  @POST
  @Path("/approval-flows/validate")
  @RequiresPermissions("flow:view")
  public List<AppApprovalIssue> validate(@PathParam("appId") String appId, AppApprovalFlowRequest body) {
    return appApprovalFlowService.validate(appId, AppApprovalFlowModel.decode(body.toMap())).stream()
      .map(AppApprovalIssue::from).toList();
  }

  @POST
  @Path("/approval-flows/{id}/status")
  @RequiresPermissions("flow:deploy")
  public AppApprovalFlowResponse status(@PathParam("appId") String appId, @PathParam("id") String id, AppApprovalFlowStatusRequest body) {
    int status = body.status() == null ? -1 : body.status();
    int revision = body.revision() == null ? -1 : body.revision();
    return AppApprovalFlowResponse.from(AppApprovalFlowModel.encode(
      appApprovalFlowService.status(appId, id, status == 2 ? "enabled" : status == 3 ? "disabled" : "invalid", revision)));
  }

  @DELETE
  @Path("/approval-flows/{id}")
  @RequiresPermissions("flow:delete")
  public Response delete(@PathParam("appId") String appId, @PathParam("id") String id) {
    appApprovalFlowService.delete(appId, id);
    return Response.noContent().build();
  }

  @GET
  @Path("/approval-directory")
  @RequiresPermissions("flow:view")
  public AppApprovalDirectory directory(@PathParam("appId") String appId) {
    appApprovalFlowService.list(appId);
    return AppApprovalDirectory.from(directoryService.directory());
  }

  @POST
  @Path("/approval-directory/resolve")
  @RequiresPermissions("flow:view")
  public List<String> resolve(AppApprovalResolveRequest body) {
    return directoryService.resolve(
      body.rules() == null ? List.of() : body.rules(),
      body.initiatorId() == null ? "" : body.initiatorId(),
      body.variables() == null ? Map.of() : body.variables());
  }

  @POST
  @Path("/forms/{form}/records/{recordId}/approvals")
  @RequiresPermissions("flow:execute")
  public Response submit(@PathParam("appId") String appId, @PathParam("form") String form, @PathParam("recordId") String recordId, AppApprovalSubmitRequest body) {
    Map<String, Object> result = runtime.submit(appId, form, recordId,
      body.flowId() == null ? "" : body.flowId(),
      body.requestKey() == null ? "" : body.requestKey());
    return Response.accepted(result).build();
  }

  @GET
  @Path("/forms/{form}/records/{recordId}/approvals")
  public List<AppApprovalItem> recordHistory(@PathParam("appId") String appId, @PathParam("form") String form, @PathParam("recordId") String recordId) {
    return approvalsService.recordHistory(appId, form, recordId).stream().map(AppApprovalItem::from).toList();
  }

  @GET
  @Path("/approval-instances/{id}")
  public AppApprovalDetail detail(@PathParam("appId") String appId, @PathParam("id") String id) {
    return AppApprovalDetail.from(approvalsService.detail(appId, id));
  }

  @POST
  @Path("/approval-instances/{id}/{action:retry|terminate}")
  @RequiresPermissions("flow:deploy")
  public Response manage(@PathParam("appId") String appId, @PathParam("id") String id, @PathParam("action") String action) {
    approvalsService.manage(appId, id, action);
    return Response.noContent().build();
  }

  @POST
  @Path("/approval-tasks/{id}/decision")
  public AppApprovalTaskResponse decide(@PathParam("appId") String appId, @PathParam("id") String id, AppApprovalDecisionRequest body) {
    return AppApprovalTaskResponse.from(approvalsService.decide(appId, id,
      body.decision() == null ? "" : body.decision(),
      body.comment() == null ? "" : body.comment(),
      body.requestKey() == null ? "" : body.requestKey()));
  }

  @POST
  @Path("/approval-instances/{id}/retry-notifications")
  @RequiresPermissions("flow:deploy")
  public Response retryNotifications(@PathParam("appId") String appId, @PathParam("id") String id) {
    approvalsService.retryNotifications(appId, id);
    return Response.noContent().build();
  }
}
