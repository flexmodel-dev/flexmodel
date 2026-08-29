package dev.flexmodel.flow.consumer;

import dev.flexmodel.common.SessionContext;
import dev.flexmodel.flow.dto.StartProcessParamEvent;
import dev.flexmodel.flow.dto.result.StartProcessResult;
import dev.flexmodel.flow.service.FlowExecutionService;
import dev.flexmodel.observability.TracingHelper;
import dev.flexmodel.scheduling.JobExecutionLogService;
import io.quarkus.vertx.ConsumeEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

/**
 * @author cjbi
 */
@Slf4j
@ApplicationScoped
public class TriggerFlowEventConsumer {

  @Inject
  FlowExecutionService flowExecutionService;

  @Inject
  JobExecutionLogService jobExecutionLogService;

  @Inject
  SessionContext sessionContext;

  @Inject
  TracingHelper tracingHelper;

  // blocking = true: 流程执行含 DB 读写与函数调用（HTTP），必须在工作线程上执行
  @ConsumeEvent(value = "flow.start", blocking = true) // 监听特定地址的事件
  public void consume(StartProcessParamEvent param) {
    sessionContext.setProjectId(param.getProjectId());
    sessionContext.setUserId(param.getUserId());
    // 恢复 span 上下文：EventBus 跨线程，OTel context 不会自动传播，
    // 用 Job 端传入的 traceId/spanId 恢复，使流程执行中的下游调用（含函数调用）在同一 trace 下
    try (TracingHelper.SpanScope span = tracingHelper.startChildSpan(
      "flow.start.consume", param.getTraceId(), param.getSpanId(), param.getProjectId())) {
      StartProcessResult result = null;
      try {
        result = flowExecutionService.startProcess(param);
        log.info("flow.start.||startProcessParam={}||result={}", param, result);
      } catch (Exception e) {
        if (param.getEventId() != null) {
          jobExecutionLogService.recordJobFailure(param.getProjectId(), param.getEventId(), e.getMessage(), e.getStackTrace(), System.currentTimeMillis() - param.getStartTime());
        }
      } finally {
        if (param.getEventId() != null) {
          jobExecutionLogService.recordJobSuccess(param.getProjectId(), param.getEventId(), result, System.currentTimeMillis() - param.getStartTime());
        }
      }
    } finally {
      // 清理会话上下文，避免状态泄漏到同一线程的下一次消息处理
      sessionContext.setProjectId(null);
      sessionContext.setProjectDatabaseName(null);
      sessionContext.setUserId(null);
    }

  }

}
