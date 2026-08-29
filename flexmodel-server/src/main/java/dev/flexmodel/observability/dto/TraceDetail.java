package dev.flexmodel.observability.dto;

import dev.flexmodel.codegen.entity.Span;
import dev.flexmodel.codegen.entity.ApiRequestLog;
import dev.flexmodel.codegen.entity.FunctionLog;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 链路追踪详情（全部 span + 关联日志）。
 *
 * @author cjbi
 */
@Data
@Builder
public class TraceDetail {
  private String traceId;
  private List<Span> spans;
  private List<ApiRequestLog> apiLogs;
  private List<FunctionLog> functionLogs;
}
