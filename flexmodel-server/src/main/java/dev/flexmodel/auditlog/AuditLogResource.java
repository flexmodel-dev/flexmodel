package dev.flexmodel.auditlog;

import dev.flexmodel.codegen.entity.AuditLog;
import dev.flexmodel.common.dto.PageDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * 审计日志查询接口。
 *
 * @author cjbi
 */
@ApplicationScoped
@Tag(name = "审计日志", description = "配置变更审计日志查询")
@Path("/projects/{projectId}/audit-logs")
public class AuditLogResource {

  @Inject
  AuditLogService auditLogService;

  @GET
  public PageDTO<AuditLog> findPage(
    @PathParam("projectId") String projectId,
    @QueryParam("action") String action,
    @QueryParam("resourceType") String resourceType,
    @QueryParam("userId") String userId,
    @QueryParam("traceId") String traceId,
    @QueryParam("page") @DefaultValue("1") Integer page,
    @QueryParam("size") @DefaultValue("20") Integer size) {
    return auditLogService.findPage(projectId, action, resourceType, userId, traceId, page, size);
  }
}
