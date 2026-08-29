package dev.flexmodel.observability.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 链路追踪列表项（按 trace_id 聚合）。
 *
 * @author cjbi
 */
@Data
@Builder
public class TraceListItem {
  private String traceId;
  private String rootName;
  private Long startTime;
  private Long totalDurationNs;
  private Integer spanCount;
  private Boolean hasError;
}
