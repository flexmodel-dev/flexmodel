package dev.flexmodel.apps.approval;

import dev.flexmodel.directory.DirectoryService;
import dev.flexmodel.common.authz.RequiresPermissions;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

import java.util.*;

import static dev.flexmodel.common.Values.*;

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
  public List<Map<String, Object>> list(@PathParam("appId") String appId) {
    return appApprovalFlowService.list(appId).stream().map(AppApprovalFlowModel::encode).toList();
  }

  @GET
  @Path("/approval-flows/{id}")
  @RequiresPermissions("flow:view")
  public Map<String, Object> get(@PathParam("appId") String appId, @PathParam("id") String id) {
    return AppApprovalFlowModel.encode(appApprovalFlowService.get(appId, id));
  }

  @POST
  @Path("/approval-flows")
  @RequiresPermissions("flow:create")
  public Response create(@PathParam("appId") String appId, Map<String, Object> body) {
    return Response.status(201).entity(AppApprovalFlowModel.encode(appApprovalFlowService.save(appId, null, AppApprovalFlowModel.decode(body)))).build();
  }

  @PUT
  @Path("/approval-flows/{id}")
  @RequiresPermissions("flow:update")
  public Map<String, Object> save(@PathParam("appId") String appId, @PathParam("id") String id, Map<String, Object> body) {
    return AppApprovalFlowModel.encode(appApprovalFlowService.save(appId, id, AppApprovalFlowModel.decode(body)));
  }

  @POST
  @Path("/approval-flows/validate")
  @RequiresPermissions("flow:view")
  public Object validate(@PathParam("appId") String appId, Map<String, Object> body) {
    return appApprovalFlowService.validate(appId, AppApprovalFlowModel.decode(body));
  }

  @POST
  @Path("/approval-flows/{id}/status")
  @RequiresPermissions("flow:deploy")
  public Object status(@PathParam("appId") String appId, @PathParam("id") String id, Map<String, Object> body) {
    int status = ((Number) body.getOrDefault("status", -1)).intValue();
    return AppApprovalFlowModel.encode(appApprovalFlowService.status(appId, id, status == 2 ? "enabled" : status == 3 ? "disabled" : "invalid", ((Number) body.getOrDefault("revision", -1)).intValue()));
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
  public Object directory(@PathParam("appId") String appId) {
    appApprovalFlowService.list(appId);
    return directoryService.directory();
  }

  @POST
  @Path("/approval-directory/resolve")
  @RequiresPermissions("flow:view")
  public Object resolve(Map<String, Object> body) {
    return directoryService.resolve(objects(body.get("rules")), text(body, "initiatorId"), object(body.get("variables")));
  }

  @POST
  @Path("/forms/{form}/records/{recordId}/approvals")
  @RequiresPermissions("flow:execute")
  public Response submit(@PathParam("appId") String appId, @PathParam("form") String form, @PathParam("recordId") String recordId, Map<String, Object> body) {
    return Response.accepted(runtime.submit(appId, form, recordId, text(body, "flowId"), text(body, "requestKey"))).build();
  }

  @GET
  @Path("/forms/{form}/records/{recordId}/approvals")
  public Object recordHistory(@PathParam("appId") String appId, @PathParam("form") String form, @PathParam("recordId") String recordId) {
    return approvalsService.recordHistory(appId, form, recordId);
  }

  @GET
  @Path("/approval-instances/{id}")
  public Object detail(@PathParam("appId") String appId, @PathParam("id") String id) {
    return approvalsService.detail(appId, id);
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
  public Object decide(@PathParam("appId") String appId, @PathParam("id") String id, Map<String, Object> body) {
    return approvalsService.decide(appId, id, text(body, "decision"), text(body, "comment"), text(body, "requestKey"));
  }

  @POST
  @Path("/approval-instances/{id}/retry-notifications")
  @RequiresPermissions("flow:deploy")
  public Response retryNotifications(@PathParam("appId") String appId, @PathParam("id") String id) {
    approvalsService.retryNotifications(appId, id);
    return Response.noContent().build();
  }
}
