package dev.flexmodel.approval;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.*;

@Path("/approvals")
@Produces(MediaType.APPLICATION_JSON)
public class ApprovalResource {
  @Inject
  ApprovalService approvalService;

  @GET
  @Path("/tasks")
  public Object tasks(@QueryParam("appId") String appId, @QueryParam("view") @DefaultValue("pending") String view,
                      @QueryParam("search") @DefaultValue("") String search, @QueryParam("page") @DefaultValue("1") int page, @QueryParam("size") @DefaultValue("30") int size) {
    return page(approvalService.tasks(appId, view, search), page, size);
  }

  @GET
  @Path("/instances")
  public Object instances(@QueryParam("appId") String appId, @QueryParam("search") @DefaultValue("") String search,
                          @QueryParam("page") @DefaultValue("1") int page, @QueryParam("size") @DefaultValue("30") int size) {
    return page(approvalService.initiated(appId, search), page, size);
  }

  @GET
  @Path("/counts")
  public Object counts(@QueryParam("appId") String appId) {
    return Map.of("pending", approvalService.tasks(appId, "pending", "").size(), "handled", approvalService.tasks(appId, "handled", "").size(), "initiated", approvalService.initiated(appId, "").size());
  }

  private Object page(List<Map<String, Object>> items, int page, int size) {
    int limit = Math.clamp(size, 1, 100), start = Math.min(items.size(), (Math.max(1, page) - 1) * limit);
    return Map.of("list", items.subList(start, Math.min(items.size(), start + limit)), "total", items.size());
  }
}
