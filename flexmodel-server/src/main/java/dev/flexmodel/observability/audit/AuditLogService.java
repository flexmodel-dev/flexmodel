package dev.flexmodel.observability.audit;

import dev.flexmodel.codegen.entity.AuditLog;
import dev.flexmodel.common.dto.PageDTO;
import dev.flexmodel.query.Expressions;
import dev.flexmodel.query.Predicate;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;

import static dev.flexmodel.codegen.System.auditLog;

/**
 * 审计日志查询服务。
 *
 * @author cjbi
 */
@Slf4j
@ApplicationScoped
@ActivateRequestContext
public class AuditLogService {

  @Inject
  AuditLogRepository auditLogRepository;

  /**
   * 分页查询审计日志，支持按操作类型、资源类型、操作人、链路ID过滤。
   */
  public PageDTO<AuditLog> findPage(String projectId, String action, String resourceType,
                                    String userId, String traceId, Integer page, Integer size) {
    Predicate filter = Expressions.TRUE;
    if (action != null && !action.isBlank()) {
      filter = filter.and(auditLog.action.eq(action));
    }
    if (resourceType != null && !resourceType.isBlank()) {
      filter = filter.and(auditLog.resourceType.eq(resourceType));
    }
    if (userId != null && !userId.isBlank()) {
      filter = filter.and(auditLog.userId.eq(userId));
    }
    if (traceId != null && !traceId.isBlank()) {
      filter = filter.and(auditLog.traceId.eq(traceId));
    }
    List<AuditLog> list = auditLogRepository.find(projectId, filter, page, size);
    long total = auditLogRepository.count(projectId, filter);
    return new PageDTO<>(list, total);
  }

  /**
   * 按链路追踪ID查询审计日志（用于链路详情关联查询）。
   */
  public List<AuditLog> findByTraceId(String projectId, String traceId) {
    return auditLogRepository.findByTraceId(projectId, traceId, 200);
  }

  /**
   * 清理指定项目超过保留天数的审计日志。
   */
  public void purgeOldLogs(String projectId, int maxDays) {
    log.info("Purging old audit logs older than {} days for project {}", maxDays, projectId);
    LocalDateTime purgeDate = LocalDateTime.now().minusDays(maxDays);
    auditLogRepository.delete(projectId, auditLog.createdAt.lte(purgeDate));
  }
}
