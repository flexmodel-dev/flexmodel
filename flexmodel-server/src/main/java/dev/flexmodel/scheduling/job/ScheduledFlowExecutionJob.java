package dev.flexmodel.scheduling.job;

import dev.flexmodel.flow.dto.StartProcessParamEvent;
import io.vertx.mutiny.core.eventbus.EventBus;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.util.Map;

/**
 * 流程执行任务
 * 用于 Quartz 定时调度执行流程实例
 *
 * @author cjbi
 */
@Slf4j
@Dependent
public class ScheduledFlowExecutionJob implements Job {

  @Inject
  EventBus eventBus;

  @Override
  @ActivateRequestContext
  public void execute(JobExecutionContext context) throws JobExecutionException {
    String projectId = context.getJobDetail().getJobDataMap().getString("projectId");
    // span 已由 ScheduledFlowExecutionJobListener 在 jobToBeExecuted 中启动并激活，
    // 此处复用其 traceId/spanId 传入 EventBus，消费端恢复链路上下文
    String flowModuleId = context.getJobDetail().getJobDataMap().getString("jobId");
    String triggerId = context.getJobDetail().getJobDataMap().getString("triggerId");
    String userId = context.getJobDetail().getJobDataMap().getString("userId");

    if (flowModuleId == null) {
      log.error("流程执行任务缺少必要参数: flowModuleId=null");
      throw new JobExecutionException("流程执行任务缺少必要参数");
    }

    log.info("开始执行定时流程任务: triggerId={}, flowModuleId={}", triggerId, flowModuleId);

    // 构建启动流程参数
    StartProcessParamEvent startProcessParam = new StartProcessParamEvent();
    startProcessParam.setFlowModuleId(flowModuleId);
    startProcessParam.setVariables(Map.of());
    startProcessParam.setProjectId(projectId);
    startProcessParam.setUserId(userId);
    startProcessParam.setTraceId((String) context.get("traceId"));
    startProcessParam.setSpanId((String) context.get("spanId"));

    // 启动流程实例
    eventBus.send("flow.start", startProcessParam);

    // 将执行结果存储到上下文中，供监听器使用
    context.setResult(Map.of(
      "success", true,
      "errMsg", "",
      "flowModuleId", flowModuleId,
      "triggerId", triggerId
    ));
  }
}
