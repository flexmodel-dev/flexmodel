package dev.flexmodel.flow.consumer;

import io.quarkus.vertx.ConsumeEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import dev.flexmodel.flow.service.FlowExecutionService;
import dev.flexmodel.flow.dto.StartProcessParamEvent;
import dev.flexmodel.flow.dto.result.StartProcessResult;
import dev.flexmodel.scheduling.JobExecutionLogService;
import dev.flexmodel.common.SessionContextHolder;

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

  @ConsumeEvent("flow.start") // 监听特定地址的事件
  public void consume(StartProcessParamEvent param) {
    SessionContextHolder.setProjectId(param.getProjectId());
    SessionContextHolder.setUserId(param.getUserId());
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
    }

  }

}
