package dev.flexmodel.observability.audit;

import dev.flexmodel.codegen.entity.AuditLog;
import dev.flexmodel.query.Predicate;

import java.util.List;

/**
 * 审计日志仓储接口。
 *
 * @author cjbi
 */
public interface AuditLogRepository {

  /**
   * 保存审计日志。
   *
   * @param projectId 项目ID
   * @param auditLog  审计日志
   * @return 保存后的审计日志
   */
  AuditLog save(String projectId, AuditLog auditLog);

  /**
   * 分页查询审计日志。
   *
   * @param projectId 项目ID
   * @param filter    查询条件
   * @param page      页码（从1开始）
   * @param size      每页大小
   * @return 审计日志列表
   */
  List<AuditLog> find(String projectId, Predicate filter, Integer page, Integer size);

  /**
   * 统计审计日志数量。
   *
   * @param projectId 项目ID
   * @param filter    查询条件
   * @return 日志数量
   */
  long count(String projectId, Predicate filter);

  /**
   * 按链路追踪ID查询审计日志（用于链路详情关联查询）。
   *
   * @param projectId 项目ID
   * @param traceId   链路追踪ID
   * @param limit     最大返回条数
   * @return 审计日志列表
   */
  List<AuditLog> findByTraceId(String projectId, String traceId, int limit);

  /**
   * 按条件删除审计日志。
   *
   * @param projectId 项目ID
   * @param filter    删除条件
   */
  void delete(String projectId, Predicate filter);
}
