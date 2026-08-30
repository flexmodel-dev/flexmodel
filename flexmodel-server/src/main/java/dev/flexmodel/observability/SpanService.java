package dev.flexmodel.observability;

import dev.flexmodel.codegen.entity.ApiRequestLog;
import dev.flexmodel.codegen.entity.FunctionLog;
import dev.flexmodel.codegen.entity.Span;
import dev.flexmodel.codegen.entity.JobExecutionLog;
import dev.flexmodel.codegen.entity.NodeInstanceLog;
import dev.flexmodel.codegen.entity.AuditLog;
import dev.flexmodel.common.dto.PageDTO;
import dev.flexmodel.observability.function.FunctionLogService;
import dev.flexmodel.observability.api.ApiRequestLogService;
import dev.flexmodel.scheduling.JobExecutionLogRepository;
import dev.flexmodel.flow.repository.NodeInstanceLogRepository;
import dev.flexmodel.observability.audit.AuditLogService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Comparator;
import java.util.Map;

import static dev.flexmodel.codegen.System.apiRequestLog;
import static dev.flexmodel.codegen.System.jobExecutionLog;

/**
 * 链路追踪查询服务。
 * <p>
 * Span 存储在平台级系统库（f_span），关联日志（API/函数）存储在项目库，按 trace_id 关联。
 *
 * @author cjbi
 */
@Slf4j
@ApplicationScoped
public class SpanService {

  @Inject
  SpanRepository spanRepository;

  @Inject
  ApiRequestLogService apiRequestLogService;

  @Inject
  FunctionLogService functionLogService;

  @Inject
  JobExecutionLogRepository jobExecutionLogRepository;

  @Inject
  NodeInstanceLogRepository nodeInstanceLogRepository;

  @Inject
  AuditLogService auditLogService;

  /**
   * 查询 trace 列表（按 trace_id 聚合 span）。
   */
  public PageDTO<TraceListItem> findTraces(String projectId, int page, int size, String traceId) {
    // 不做服务端分页：span 按 trace_id 聚合后再分页需要 DB 侧聚合支持，
    // 这里一次性拉取近期 span 窗口，Java 侧聚合全部 trace 返回，由前端客户端分页。
    int limit = 5000;
    List<Span> spans = spanRepository.findByProject(projectId, null, null, traceId, limit);

    Map<String, TraceListItemBuilder> byTrace = new LinkedHashMap<>();
    for (Span s : spans) {
      TraceListItemBuilder b = byTrace.computeIfAbsent(s.getTraceId(), k -> new TraceListItemBuilder());
      b.spanCount++;
      if (b.rootName == null || s.getParentId() == null) {
        if (b.rootName == null) {
          b.rootName = s.getName();
        }
        if (s.getParentId() == null) {
          b.rootName = s.getName();
        }
      }
      if (b.startTime == null || (s.getStartTime() != null && s.getStartTime() < b.startTime)) {
        b.startTime = s.getStartTime();
      }
      if (s.getDurationNs() != null) {
        b.totalDurationNs = (b.totalDurationNs == null ? 0 : b.totalDurationNs) + s.getDurationNs();
      }
      if ("ERROR".equals(s.getStatus())) {
        b.hasError = true;
      }
    }

    List<TraceListItem> all = new ArrayList<>();
    for (var e : byTrace.entrySet()) {
      var b = e.getValue();
      all.add(TraceListItem.builder()
        .traceId(e.getKey())
        .rootName(b.rootName)
        .startTime(b.startTime)
        .totalDurationNs(b.totalDurationNs)
        .spanCount(b.spanCount)
        .hasError(b.hasError)
        .build());
    }
    // 按起始时间倒序，全部返回，total 即聚合后的 trace 总数
    all.sort(Comparator.comparing(TraceListItem::getStartTime, Comparator.nullsLast(Comparator.reverseOrder())));
    return new PageDTO<>(all, (long) all.size());
  }

  /**
   * 查询 trace 详情（全部 span + 关联日志）。
   */
  public TraceDetail findTraceDetail(String projectId, String traceId) {
    List<Span> spans = spanRepository.findByTraceId(projectId, traceId);

    // 关联日志从项目库按 trace_id 查询
    List<ApiRequestLog> apiLogs = List.of();
    List<FunctionLog> functionLogs = List.of();
    List<JobExecutionLog> jobExecutionLogs = List.of();
    List<NodeInstanceLog> nodeInstanceLogs = List.of();
    List<AuditLog> auditLogs = List.of();
    try {
      apiLogs = apiRequestLogService.find(projectId, apiRequestLog.traceId.eq(traceId), 1, 200);
    } catch (Exception e) {
      log.debug("Failed to fetch api logs for trace {}", traceId, e);
    }
    try {
      functionLogs = functionLogService.findFunctionLogs(projectId, 1, 200,
        null, null, null, null, traceId, null).list();
    } catch (Exception e) {
      log.debug("Failed to fetch function logs for trace {}", traceId, e);
    }
    try {
      jobExecutionLogs = jobExecutionLogRepository.find(projectId, jobExecutionLog.traceId.eq(traceId), 1, 200);
    } catch (Exception e) {
      log.debug("Failed to fetch job execution logs for trace {}", traceId, e);
    }
    try {
      nodeInstanceLogs = nodeInstanceLogRepository.findByTraceId(projectId, traceId, 200);
    } catch (Exception e) {
      log.debug("Failed to fetch node instance logs for trace {}", traceId, e);
    }
    try {
      auditLogs = auditLogService.findByTraceId(projectId, traceId);
    } catch (Exception e) {
      log.debug("Failed to fetch audit logs for trace {}", traceId, e);
    }

    return TraceDetail.builder()
      .traceId(traceId)
      .spans(spans)
      .apiLogs(apiLogs)
      .functionLogs(functionLogs)
      .jobExecutionLogs(jobExecutionLogs)
      .nodeInstanceLogs(nodeInstanceLogs)
      .auditLogs(auditLogs)
      .build();
  }

  private static class TraceListItemBuilder {
    String rootName;
    Long startTime;
    Long totalDurationNs;
    int spanCount;
    boolean hasError;
  }
}
