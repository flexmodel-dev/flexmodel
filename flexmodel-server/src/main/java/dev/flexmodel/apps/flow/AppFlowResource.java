package dev.flexmodel.apps.flow;

import dev.flexmodel.apps.flow.dto.*;
import dev.flexmodel.common.SessionContext;
import dev.flexmodel.common.authz.RequiresPermissions;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

import java.util.*;

import static dev.flexmodel.common.Values.*;

@Path("/apps/{appId}")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AppFlowResource {
  @Inject
  AppFlowService flows;
  @Inject
  AppFlowRuntime runtime;
  @Inject
  AppApprovalService tasks;
  @Inject
  AppFlowBindings bindings;
  @Inject
  AppFlowInstanceRepository instances;
  @Inject
  SessionContext current;

  private AppFlowResponse response(Map<String, Object> flow) {
    return AppFlowResponse.from(AppFlowModel.encode(flow));
  }

  public record Publication(Integer revision) {
  }

  public record Enablement(boolean enabled, Integer revision) {
  }

  @GET
  @Path("/flows")
  @RequiresPermissions("flow:view")
  public List<AppFlowResponse> list(@PathParam("appId") String app) {
    return flows.list(app).stream().map(this::response).toList();
  }

  @GET
  @Path("/flows/cron-preview")
  @RequiresPermissions("flow:view")
  public List<String> preview(@QueryParam("cron") String cron, @QueryParam("timezone") String timezone, @QueryParam("startAt") String start, @QueryParam("endAt") String end) {
    var cfg = new LinkedHashMap<String, Object>();
    cfg.put("type", "SCHEDULE");
    cfg.put("cron", Objects.toString(cron, ""));
    cfg.put("timezone", Objects.toString(timezone, "Asia/Shanghai"));
    cfg.put("startAt", Objects.toString(start, ""));
    cfg.put("endAt", Objects.toString(end, ""));
    FlowTrigger.validate(cfg);
    try {
      var expression = new org.quartz.CronExpression(cron);
      expression.setTimeZone(TimeZone.getTimeZone(java.time.ZoneId.of(timezone == null || timezone.isBlank() ? "Asia/Shanghai" : timezone)));
      Date cursor = Date.from(java.time.Instant.now());
      if (start != null && !start.isBlank() && java.time.Instant.parse(start).isAfter(cursor.toInstant()))
        cursor = Date.from(java.time.Instant.parse(start).minusMillis(1));
      var times = new ArrayList<String>();
      for (int i = 0; i < 5; i++) {
        cursor = expression.getNextValidTimeAfter(cursor);
        if (cursor == null || end != null && !end.isBlank() && cursor.toInstant().isAfter(java.time.Instant.parse(end)))
          break;
        times.add(cursor.toInstant().toString());
      }
      return times;
    } catch (java.text.ParseException e) {
      throw new dev.flexmodel.common.ValidationException("Cron 表达式无效");
    }
  }

  @GET
  @Path("/flows/{id}")
  @RequiresPermissions("flow:view")
  public AppFlowResponse get(@PathParam("appId") String app, @PathParam("id") String id) {
    return response(flows.get(app, id));
  }

  @POST
  @Path("/flows")
  @RequiresPermissions("flow:create")
  public Response create(@PathParam("appId") String app, AppFlowRequest body) {
    return Response.status(201).entity(response(flows.save(app, null, AppFlowModel.decode(body.toMap())))).build();
  }

  @PUT
  @Path("/flows/{id}")
  @RequiresPermissions("flow:update")
  public AppFlowResponse save(@PathParam("appId") String app, @PathParam("id") String id, AppFlowRequest body) {
    return response(flows.save(app, id, AppFlowModel.decode(body.toMap())));
  }

  @POST
  @Path("/flows/validate")
  @RequiresPermissions("flow:view")
  public List<AppApprovalIssue> validate(@PathParam("appId") String app, AppFlowRequest body) {
    return flows.validate(app, AppFlowModel.decode(body.toMap())).stream().map(AppApprovalIssue::from).toList();
  }

  @POST
  @Path("/flows/{id}/publish")
  @RequiresPermissions("flow:deploy")
  public AppFlowResponse publish(@PathParam("appId") String app, @PathParam("id") String id, Publication body) {
    return response(flows.publish(app, id, body.revision() == null ? -1 : body.revision()));
  }

  @PUT
  @Path("/flows/{id}/enabled")
  @RequiresPermissions("flow:deploy")
  public AppFlowResponse enabled(@PathParam("appId") String app, @PathParam("id") String id, Enablement body) {
    return response(flows.status(app, id, body.enabled() ? "enabled" : "disabled", body.revision() == null ? -1 : body.revision()));
  }

  @DELETE
  @Path("/flows/{id}")
  @RequiresPermissions("flow:delete")
  public Response delete(@PathParam("appId") String app, @PathParam("id") String id) {
    flows.delete(app, id);
    return Response.noContent().build();
  }

  @POST
  @Path("/flows/{id}/instances")
  @RequiresPermissions("flow:execute")
  public Response start(@PathParam("appId") String app, @PathParam("id") String id, AppFlowStartRequest body) {
    var run = runtime.manual(app, id, body.recordId(), body.requestKey(), body.input() == null ? Map.of() : body.input());
    return Response.accepted(Map.of("id", text(run, "id"), "status", "SUBMITTING".equals(text(run, "status")) ? "QUEUED" : text(run, "status"))).build();
  }

  @GET
  @Path("/flows/{id}/trigger-logs")
  @RequiresPermissions("flow:view")
  public List<Map<String, Object>> logs(@PathParam("appId") String app, @PathParam("id") String id) {
    flows.get(app, id);
    return bindings.logs(app, id).stream().map(row -> Map.<String, Object>of("id", text(row, "id"), "state", text(row, "state"), "sourceId", text(row, "source_id"), "instanceId", text(row, "instance_id"), "reason", text(row, "reason"), "createdAt", row.get("created_at"))).toList();
  }

  @GET
  @Path("/flow-instances")
  @RequiresPermissions("flow:view")
  public dev.flexmodel.common.dto.PageDTO<AppApprovalItem> instances(@PathParam("appId") String app, @QueryParam("flowId") String flow, @QueryParam("formName") String form, @QueryParam("recordId") String record, @QueryParam("page") @DefaultValue("1") int page, @QueryParam("size") @DefaultValue("30") int size) {
    flows.list(app);
    var rows = instances.findAll(app).stream().filter(r -> flow == null || flow.equals(text(r, "flow_id")))
      .filter(r -> form == null || form.equals(text(r, "form_name"))).filter(r -> record == null || record.equals(text(r, "record_id")))
      .filter(r -> tasks.canView(app, r))
      .sorted(Comparator.comparing((Map<String, Object> r) -> text(r, "created_at")).reversed()).map(r -> AppApprovalItem.from(tasks.summary(app, "", r))).toList();
    int limit = Math.clamp(size, 1, 100), from = (int) Math.min(rows.size(), (long) (Math.max(page, 1) - 1) * limit);
    return new dev.flexmodel.common.dto.PageDTO<>(rows.subList(from, Math.min(rows.size(), from + limit)), (long) rows.size());
  }

  @GET
  @Path("/flows/{id}/instances")
  @RequiresPermissions("flow:view")
  public dev.flexmodel.common.dto.PageDTO<AppApprovalItem> forFlow(@PathParam("appId") String app, @PathParam("id") String id, @QueryParam("page") @DefaultValue("1") int page, @QueryParam("size") @DefaultValue("30") int size) {
    return instances(app, id, null, null, page, size);
  }

  @GET
  @Path("/flow-instances/{id}")
  public AppApprovalDetail detail(@PathParam("appId") String app, @PathParam("id") String id) {
    return AppApprovalDetail.from(tasks.detail(app, id));
  }

  @POST
  @Path("/flow-instances/{id}/{action:retry|terminate}")
  @RequiresPermissions("flow:deploy")
  public Response manage(@PathParam("appId") String app, @PathParam("id") String id, @PathParam("action") String action) {
    tasks.manage(app, id, action);
    return Response.noContent().build();
  }

  @POST
  @Path("/flow-tasks/{id}/decisions")
  public AppApprovalTaskResponse decide(@PathParam("appId") String app, @PathParam("id") String id, AppApprovalDecisionRequest body) {
    return AppApprovalTaskResponse.from(tasks.decide(app, id, Objects.toString(body.decision(), ""), Objects.toString(body.comment(), ""), Objects.toString(body.requestKey(), "")));
  }

  @POST
  @Path("/flow-instances/{id}/retry-notifications")
  @RequiresPermissions("flow:deploy")
  public Response retryNotifications(@PathParam("appId") String app, @PathParam("id") String id) {
    tasks.retryNotifications(app, id);
    return Response.noContent().build();
  }
}
