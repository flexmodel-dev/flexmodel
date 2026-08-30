package dev.flexmodel.observability;

import dev.flexmodel.codegen.entity.Span;

import java.util.List;

/**
 * 链路追踪 Span 查询仓储（平台级系统库）。
 *
 * @author cjbi
 */
public interface SpanRepository {

  /**
   * 按项目 + 时间范围查询 span（用于 trace 列表聚合）
   */
  List<Span> findByProject(String projectId, Long startFrom, Long startTo, int limit);

  /**
   * 按项目 + 时间范围 + traceId 模糊查询 span
   */
  List<Span> findByProject(String projectId, Long startFrom, Long startTo, String traceId, int limit);

  /**
   * 按 trace_id 查询全部 span
   */
  List<Span> findByTraceId(String traceId);

  /**
   * 清理指定项目超过保留天数的 span（平台级系统库，按 project_id 过滤）。
   *
   * @param projectId 项目ID
   * @param maxDays   保留天数
   */
  void purgeOldLogs(String projectId, int maxDays);

}
