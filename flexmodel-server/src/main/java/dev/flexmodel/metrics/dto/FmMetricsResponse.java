package dev.flexmodel.metrics.dto;

import lombok.*;

/**
 * @author cjbi
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FmMetricsResponse {

  /**
   * 请求数量
   */
  private int requestCount;
  /**
   * 模型数量
   */
  private int modelCount;
  /**
   * 分支数量
   */
  private int branchCount;
  /**
   * 流程数量
   */
  private int flowDefCount;
  /**
   * 流程成功数量
   */
  private int flowExecCount;
  /**
   * 触发器数量
   */
  private int triggerTotalCount;
  /**
   * 触发器成功数量
   */
  private int jobSuccessCount;
  /**
   * 触发器失败数量
   */
  private int jobFailureCount;


}
