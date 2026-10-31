package dev.flexmodel.apps.approval;

import dev.flexmodel.apps.approval.dto.AppApprovalCounts;
import dev.flexmodel.apps.approval.dto.AppApprovalItem;
import dev.flexmodel.apps.approval.dto.AppApprovalPage;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.Map;

@Path("/approvals")
@Produces(MediaType.APPLICATION_JSON)
public class AppApprovalResource {
  @Inject
  AppApprovalService appApprovalService;

  @GET
  @Path("/tasks")
  public AppApprovalPage<AppApprovalItem> tasks(@QueryParam("appId") String appId, @QueryParam("view") @DefaultValue("pending") String view,
                                                @QueryParam("search") @DefaultValue("") String search, @QueryParam("page") @DefaultValue("1") int page, @QueryParam("size") @DefaultValue("30") int size) {
    return page(appApprovalService.tasks(appId, view, search), page, size);
  }

  @GET
  @Path("/instances")
  public AppApprovalPage<AppApprovalItem> instances(@QueryParam("appId") String appId, @QueryParam("search") @DefaultValue("") String search,
                                                    @QueryParam("page") @DefaultValue("1") int page, @QueryParam("size") @DefaultValue("30") int size) {
    return page(appApprovalService.initiated(appId, search), page, size);
  }

  @GET
  @Path("/counts")
  public AppApprovalCounts counts(@QueryParam("appId") String appId) {
    return new AppApprovalCounts(
      appApprovalService.tasks(appId, "pending", "").size(),
      appApprovalService.tasks(appId, "handled", "").size(),
      appApprovalService.initiated(appId, "").size());
  }

  private AppApprovalPage<AppApprovalItem> page(List<Map<String, Object>> items, int page, int size) {
    int limit = Math.clamp(size, 1, 100), start = Math.min(items.size(), (Math.max(1, page) - 1) * limit);
    List<AppApprovalItem> list = items.subList(start, Math.min(items.size(), start + limit)).stream()
      .map(AppApprovalItem::from).toList();
    return new AppApprovalPage<>(list, items.size());
  }
}
