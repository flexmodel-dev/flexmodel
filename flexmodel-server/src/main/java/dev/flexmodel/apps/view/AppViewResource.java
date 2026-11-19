package dev.flexmodel.apps.view;

import dev.flexmodel.apps.view.dto.AppViewMoveRequest;
import dev.flexmodel.apps.view.dto.AppViewQueryRequest;
import dev.flexmodel.apps.view.dto.AppViewQueryResponse;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.Map;

/**
 * 表单视图数据 API。
 *
 * @author cjbi
 */
@Tag(name = "应用", description = "应用表单多类型视图")
@Path("/apps/{appId}/forms/{formName}/views")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AppViewResource {

  @Inject
  AppViewService appViewService;

  @Operation(summary = "查询视图数据")
  @POST
  @Path("/{viewId}/query")
  public AppViewQueryResponse query(
    @PathParam("appId") String appId,
    @PathParam("formName") String formName,
    @PathParam("viewId") String viewId,
    AppViewQueryRequest request
  ) {
    return appViewService.query(appId, formName, viewId, request);
  }

  @Operation(summary = "拖拽更新视图记录")
  @POST
  @Path("/{viewId}/records/{recordId}/move")
  public Map<String, Object> move(
    @PathParam("appId") String appId,
    @PathParam("formName") String formName,
    @PathParam("viewId") String viewId,
    @PathParam("recordId") String recordId,
    AppViewMoveRequest request
  ) {
    return appViewService.move(appId, formName, viewId, recordId, request);
  }
}
