package dev.flexmodel.flow.consumer;

import dev.flexmodel.common.SessionContext;
import dev.flexmodel.flow.dto.StartProcessParamEvent;
import dev.flexmodel.flow.dto.result.StartProcessResult;
import dev.flexmodel.flow.service.FlowExecutionService;
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

  @ConsumeEvent("flow.start") // 监听特定地址的事件
  public void consume(StartProcessParamEvent param) {
    sessionContext.setProjectId(param.getProjectId());
    sessionContext.setUserId(param.getUserId());
    StartProcessResult result = null;
    try {
      result = flowExecutionService.startProcess(param);
      log.info("flow.start.||startProcessParam={}||result={}", param, result);
    } catch (Exception e) {
      if (param.getEventId() != null) {
        jobExecutionLogService.recordJobFailure(param.getEventId(), e.getMessage(), e.getStackTrace(), System.currentTimeMillis() - param.getStartTime());
      }
    } finally {
      if (param.getEventId() != null) {
        jobExecutionLogService.recordJobSuccess(param.getEventId(), result, System.currentTimeMillis() - param.getStartTime());
      }
      // 清理会话上下文，避免状态泄漏到同一线程的下一次消息处理
      sessionContext.setProjectId(null);
      sessionContext.setProjectDatabaseName(null);
      sessionContext.setUserId(null);
    }

  }

}
