package dev.flexmodel.approval;

import dev.flexmodel.directory.DirectoryService;
import dev.flexmodel.approval.ApprovalService;
import dev.flexmodel.common.authz.RequiresPermissions;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

import java.util.*;

import static dev.flexmodel.approval.ApprovalJson.*;
import static dev.flexmodel.query.Expressions.field;

@Path("/apps/{appId}")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ApprovalFlowResource {
  @Inject
  ApprovalFlowService approvalFlows;
  @Inject
  ApprovalRuntime runtime;
  @Inject
  ApprovalRepository repository;
  @Inject
  DirectoryService directory;
  @Inject
  ApprovalService approvals;

  @GET
  @Path("/approval-flows")
  @RequiresPermissions("flow:view")
  public List<Map<String, Object>> list(@PathParam("appId") String appId) {
    return approvalFlows.list(appId).stream().map(ApprovalFlowModel::encode).toList();
  }

  @GET
  @Path("/approval-flows/{id}")
  @RequiresPermissions("flow:view")
  public Map<String, Object> get(@PathParam("appId") String appId, @PathParam("id") String id) {
    return ApprovalFlowModel.encode(approvalFlows.get(appId, id));
  }

  @POST
  @Path("/approval-flows")
  @RequiresPermissions("flow:create")
  public Response create(@PathParam("appId") String appId, Map<String, Object> body) {
    return Response.status(201).entity(ApprovalFlowModel.encode(approvalFlows.save(appId, null, ApprovalFlowModel.decode(body)))).build();
  }

  @PUT
  @Path("/approval-flows/{id}")
  @RequiresPermissions("flow:update")
  public Map<String, Object> save(@PathParam("appId") String appId, @PathParam("id") String id, Map<String, Object> body) {
    return ApprovalFlowModel.encode(approvalFlows.save(appId, id, ApprovalFlowModel.decode(body)));
  }

  @POST
  @Path("/approval-flows/validate")
  @RequiresPermissions("flow:view")
  public Object validate(@PathParam("appId") String appId, Map<String, Object> body) {
    return approvalFlows.validate(appId, ApprovalFlowModel.decode(body));
  }

  @POST
  @Path("/approval-flows/{id}/status")
  @RequiresPermissions("flow:deploy")
  public Object status(@PathParam("appId") String appId, @PathParam("id") String id, Map<String, Object> body) {
    int status = ((Number) body.getOrDefault("status", -1)).intValue();
    return ApprovalFlowModel.encode(approvalFlows.status(appId, id, status == 2 ? "enabled" : status == 3 ? "disabled" : "invalid", ((Number) body.getOrDefault("revision", -1)).intValue()));
  }

  @DELETE
  @Path("/approval-flows/{id}")
  @RequiresPermissions("flow:delete")
  public Response delete(@PathParam("appId") String appId, @PathParam("id") String id) {
    approvalFlows.delete(appId, id);
    return Response.noContent().build();
  }

  @GET
  @Path("/approval-directory")
  @RequiresPermissions("flow:view")
  public Object directory(@PathParam("appId") String appId) {
    approvalFlows.list(appId);
    return directory.directory();
  }

  @POST
  @Path("/approval-directory/resolve")
  @RequiresPermissions("flow:view")
  public Object resolve(Map<String, Object> body) {
    return directory.resolve(objects(body.get("rules")), text(body, "initiatorId"), object(body.get("variables")));
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
    return repository.list(appId, "f_approval_instance", field("form_name").eq(form).and(field("record_id").eq(recordId))).stream()
      .filter(run -> text(run, "parent_task_id").isEmpty()).map(run -> approvals.summary(appId, "", run)).toList();
  }

  @GET
  @Path("/approval-instances/{id}")
  public Object detail(@PathParam("appId") String appId, @PathParam("id") String id) {
    return approvals.detail(appId, id);
  }

  @POST
  @Path("/approval-instances/{id}/{action:retry|terminate}")
  @RequiresPermissions("flow:deploy")
  public Response manage(@PathParam("appId") String appId, @PathParam("id") String id, @PathParam("action") String action) {
    approvals.manage(appId, id, action);
    return Response.noContent().build();
  }

  @POST
  @Path("/approval-tasks/{id}/decision")
  public Object decide(@PathParam("appId") String appId, @PathParam("id") String id, Map<String, Object> body) {
    return approvals.decide(appId, id, text(body, "decision"), text(body, "comment"), text(body, "requestKey"));
  }

  @POST
  @Path("/approval-instances/{id}/retry-notifications")
  @RequiresPermissions("flow:deploy")
  public Response retryNotifications(@PathParam("appId") String appId, @PathParam("id") String id) {
    approvals.retryNotifications(appId, id);
    return Response.noContent().build();
  }
}
