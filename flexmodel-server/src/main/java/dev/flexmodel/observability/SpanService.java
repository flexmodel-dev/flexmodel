package dev.flexmodel.observability;

import dev.flexmodel.codegen.entity.ApiRequestLog;
import dev.flexmodel.codegen.entity.FunctionLog;
import dev.flexmodel.codegen.entity.Span;
import dev.flexmodel.common.dto.PageDTO;
import dev.flexmodel.observability.log.FunctionLogService;
import dev.flexmodel.observability.log.ApiRequestLogService;
import dev.flexmodel.observability.dto.TraceDetail;
import dev.flexmodel.observability.dto.TraceListItem;
import dev.flexmodel.query.Expressions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dev.flexmodel.codegen.System.apiRequestLog;
import static dev.flexmodel.codegen.System.functionLog;

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

  /**
   * 查询 trace 列表（按 trace_id 聚合 span）。
   */
  public PageDTO<TraceListItem> findTraces(String projectId, int page, int size, String traceId) {
    // 拉取该项目近期 span，在 Java 侧按 trace_id 聚合
    int limit = Math.min(page * size, 5000);
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

    int from = Math.min((page - 1) * size, all.size());
    int to = Math.min(from + size, all.size());
    List<TraceListItem> pageList = all.subList(from, to);
    return new PageDTO<>(pageList, (long) all.size());
  }

  /**
   * 查询 trace 详情（全部 span + 关联日志）。
   */
  public TraceDetail findTraceDetail(String projectId, String traceId) {
    List<Span> spans = spanRepository.findByTraceId(traceId);

    // 关联日志从项目库按 trace_id 查询
    List<ApiRequestLog> apiLogs = List.of();
    List<FunctionLog> functionLogs = List.of();
    try {
      apiLogs = apiRequestLogService.find(projectId, apiRequestLog.traceId.eq(traceId), 1, 200);
    } catch (Exception e) {
      log.debug("Failed to fetch api logs for trace {}", traceId, e);
    }
    try {
      functionLogs = functionLogService.findFunctionLogs(projectId, 1, 200,
        null, null, null, null, null, traceId, null).list();
    } catch (Exception e) {
      log.debug("Failed to fetch function logs for trace {}", traceId, e);
    }

    return TraceDetail.builder()
      .traceId(traceId)
      .spans(spans)
      .apiLogs(apiLogs)
      .functionLogs(functionLogs)
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
