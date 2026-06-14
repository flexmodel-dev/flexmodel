package dev.flexmodel.scheduling.job;

import dev.flexmodel.functions.FunctionService;
import dev.flexmodel.functions.dto.FunctionInvokeRequest;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.util.Map;

/**
 * 云函数执行任务
 * 用于 Quartz 定时调度执行云函数
 *
 * @author cjbi
 */
@Slf4j
public class ScheduledFunctionExecutionJob implements Job {

  @Inject
  FunctionService functionService;

  @Override
  public void execute(JobExecutionContext context) throws JobExecutionException {
    try {
      String functionName = context.getJobDetail().getJobDataMap().getString("jobId");
      String triggerId = context.getJobDetail().getJobDataMap().getString("triggerId");
      String projectId = context.getJobDetail().getJobDataMap().getString("projectId");

      if (functionName == null) {
        log.error("云函数执行任务缺少必要参数: functionName=null");
        throw new JobExecutionException("云函数执行任务缺少必要参数");
      }

      log.info("开始执行定时云函数任务: triggerId={}, functionName={}", triggerId, functionName);

      FunctionInvokeRequest invokeReq = new FunctionInvokeRequest();
      invokeReq.setMethod("POST");
      invokeReq.setBody(Map.of("triggerId", triggerId, "triggerTime", System.currentTimeMillis()));

      functionService.invoke(projectId, functionName, invokeReq);

      context.setResult(Map.of(
        "success", true,
        "errMsg", "",
        "functionName", functionName,
        "triggerId", triggerId
      ));

    } catch (Exception e) {
      log.error("执行定时云函数任务失败", e);

      context.setResult(Map.of(
        "success", false,
        "error", e.getMessage(),
        "exception", e.getClass().getSimpleName()
      ));

      throw new JobExecutionException("执行定时云函数任务失败", e);
    }
  }
}
