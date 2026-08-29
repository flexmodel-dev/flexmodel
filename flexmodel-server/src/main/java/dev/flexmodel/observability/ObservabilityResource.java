package dev.flexmodel.observability;

import dev.flexmodel.common.dto.PageDTO;
import dev.flexmodel.observability.dto.TraceDetail;
import dev.flexmodel.observability.dto.TraceListItem;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * 可观测性 —— 链路追踪查询接口。
 * <p>
 * 统一入口，按 trace_id 聚合 span，并关联 API 日志 / 函数日志。
 *
 * @author cjbi
 */
@Tag(name = "可观测性", description = "链路追踪与可观测性")
@Path("/projects/{projectId}/observability")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ObservabilityResource {

  @Inject
  SpanService spanService;

  @Operation(summary = "查询链路追踪列表")
  @GET
  @Path("/traces")
  public PageDTO<TraceListItem> findTraces(
    @PathParam("projectId") String projectId,
    @QueryParam("page") @DefaultValue("1") int page,
    @QueryParam("size") @DefaultValue("20") int size,
    @QueryParam("traceId") String traceId
  ) {
    return spanService.findTraces(projectId, page, size, traceId);
  }

  @Operation(summary = "查询链路追踪详情")
  @GET
  @Path("/traces/{traceId}")
  public TraceDetail findTraceDetail(
    @PathParam("projectId") String projectId,
    @PathParam("traceId") String traceId
  ) {
    return spanService.findTraceDetail(projectId, traceId);
  }
}
