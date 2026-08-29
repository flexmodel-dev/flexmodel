package dev.flexmodel.observability.function;

import dev.flexmodel.codegen.entity.FunctionLog;
import dev.flexmodel.common.dto.PageDTO;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 函数执行日志查询接口。
 *
 * @author cjbi
 */
@Tag(name = "函数日志", description = "边缘函数执行日志查询")
@Path("/projects/{projectId}/functions/logs")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class FunctionLogResource {

  @Inject
  FunctionLogService functionLogService;

  @Operation(summary = "查询函数执行日志")
  @GET
  public PageDTO<FunctionLog> findFunctionLogs(
    @PathParam("projectId") String projectId,
    @QueryParam("page") @DefaultValue("1") int page,
    @QueryParam("size") @DefaultValue("50") int size,
    @QueryParam("functionName") String functionName,
    @QueryParam("level") String level,
    @QueryParam("dateRange") String dateRange,
    @QueryParam("traceId") String traceId,
    @QueryParam("keyword") String keyword
  ) {
    LocalDateTime startDate = null;
    LocalDateTime endDate = null;
    if (dateRange != null) {
      try {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String[] arr = dateRange.split(",");
        startDate = LocalDateTime.parse(arr[0], formatter);
        endDate = LocalDateTime.parse(arr[1], formatter);
      } catch (Exception e) {
        startDate = null;
        endDate = null;
      }
    }
    return functionLogService.findFunctionLogs(projectId, page, size, functionName, level,
      startDate, endDate, traceId, keyword);
  }
}
