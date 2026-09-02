package dev.flexmodel.scheduling.job;

import dev.flexmodel.codegen.entity.JobExecutionLog;
import dev.flexmodel.scheduling.JobExecutionLogService;
import dev.flexmodel.common.trace.TraceContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobListener;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 定时流程执行作业监听器
 * 用于记录作业执行的完整生命周期日志
 *
 * @author cjbi
 */
@Slf4j
@ApplicationScoped
public class ScheduledFlowExecutionJobListener implements JobListener {

  @Inject
  JobExecutionLogService jobExecutionLogService;

  private static final String EXECUTION_LOG_ID_KEY = "executionLogId";
  static final String SPAN_SCOPE_KEY = "traceScope";

  @Inject
  TraceContext traceContext;

  @Override
  public String getName() {
    return "ScheduledFlowExecutionJobListener";
  }

  @Override
  public void jobToBeExecuted(JobExecutionContext context) {
    try {
      log.debug("作业即将执行: {}", context.getJobDetail().getKey());

      // 从 JobDataMap 中获取参数
      String triggerId = context.getJobDetail().getJobDataMap().getString("triggerId");
      String jobId = context.getJobDetail().getJobDataMap().getString("jobId");
      String jobGroup = context.getJobDetail().getJobDataMap().getString("jobGroup");
      String jobType = context.getJobDetail().getJobDataMap().getString("jobType");
      String jobName = context.getJobDetail().getDescription();
      String schedulerName = context.getScheduler().getSchedulerName();
      String instanceName = context.getScheduler().getSchedulerInstanceId();
      Long firedTime = context.getFireTime().getTime();
      Long scheduledTime = context.getScheduledFireTime().getTime();
      String projectId = context.getJobDetail().getJobDataMap().getString("projectId");

      // 生成根 span 的 traceId/spanId：Quartz Job 为非 HTTP 入口，无自动 span 上下文。
      // ScopedValue 无法跨 jobToBeExecuted→execute→jobWasExecuted 三个独立回调维持绑定，
      // 此处仅生成 scope 并存入 context；实际绑定在 Job.execute() 内通过 TraceContextHolder.with 完成，
      // 保证 recordJobStart 拿到 traceId，下游（函数调用的 traceparent 注入）在 execute 作用域内可见。
      TraceContext.TraceScope traceScope = traceContext.createScope();
      context.put("traceId", traceScope.traceId());
      context.put("spanId", traceScope.spanId());
      context.put(SPAN_SCOPE_KEY, traceScope);

      // 获取输入数据
      Object inputData = extractInputData(context);

      // 记录作业开始执行
      JobExecutionLog executionLog = jobExecutionLogService.recordJobStart(
        triggerId, jobId, jobGroup, jobType, jobName,
        schedulerName, instanceName, firedTime, scheduledTime,
        inputData, projectId, traceScope.traceId()
      );

      // 将执行日志ID存储到上下文中，供后续使用
      context.put("projectId", projectId);
      context.put(EXECUTION_LOG_ID_KEY, executionLog.getId());

      log.info("已记录作业开始执行: triggerId={}, jobId={}, logId={}",
        triggerId, jobId, executionLog.getId());

    } catch (Exception e) {
      log.error("记录作业开始执行失败", e);
    }
  }

  @Override
  public void jobExecutionVetoed(JobExecutionContext context) {
    try {
      log.warn("作业执行被否决: {}", context.getJobDetail().getKey());

      String logId = (String) context.get(EXECUTION_LOG_ID_KEY);
      Object projectIdObj = context.get("projectId");
      String projectId = projectIdObj != null ? projectIdObj.toString() : null;
      if (logId != null) {
        // 记录作业被否决
        jobExecutionLogService.recordJobFailure(
          projectId,
          logId,
          "作业执行被否决",
          Map.of("reason", "Job execution vetoed", "vetoTime", LocalDateTime.now()),
          System.currentTimeMillis() - context.getFireTime().getTime()
        );
      }
    } catch (Exception e) {
      log.error("记录作业否决失败", e);
    }
  }

  @Override
  public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
    try {
      String logId = (String) context.get(EXECUTION_LOG_ID_KEY);
      Object projectIdObj = context.get("projectId");
      String projectId = projectIdObj != null ? projectIdObj.toString() : null;
      if (logId == null) {
        log.warn("未找到执行日志ID，无法记录作业执行结果: {}", context.getJobDetail().getKey());
        return;
      }

      long executionDuration = System.currentTimeMillis() - context.getFireTime().getTime();

      if (jobException == null) {
        // 作业执行成功
        log.info("作业执行成功: {}, 耗时: {}ms", context.getJobDetail().getKey(), executionDuration);

        // 获取输出数据
        Object outputData = extractOutputData(context);

        jobExecutionLogService.recordJobSuccess(projectId, logId, outputData, executionDuration);
      } else {
        // 作业执行失败
        log.error("作业执行失败: {}, 耗时: {}ms", context.getJobDetail().getKey(), executionDuration, jobException);

        // 记录失败信息
        String errorMessage = jobException.getMessage();
        Object errorStackTrace = Map.of(
          "exception", jobException.getClass().getSimpleName(),
          "message", errorMessage != null ? errorMessage : "Unknown error",
          "stackTrace", getStackTrace(jobException)
        );

        jobExecutionLogService.recordJobFailure(projectId, logId, errorMessage, errorStackTrace, executionDuration);
      }
    } catch (Exception e) {
      log.error("记录作业执行结果失败", e);
    } finally {
      // ScopedValue 由 Job.execute() 内的 TraceContextHolder.with 自动清理，
      // 跨回调无法维持绑定，此处无需（也无法）关闭 span。
    }
  }

  /**
   * 提取输入数据
   */
  private Object extractInputData(JobExecutionContext context) {
    try {
      Map<String, Object> inputData = new HashMap<>();
      inputData.put("jobDataMap", context.getJobDetail().getJobDataMap().getWrappedMap());
      inputData.put("triggerDataMap", context.getTrigger().getJobDataMap().getWrappedMap());
      inputData.put("fireTime", context.getFireTime());
      inputData.put("scheduledFireTime", context.getScheduledFireTime());
      inputData.put("previousFireTime", context.getPreviousFireTime());
      inputData.put("nextFireTime", context.getNextFireTime());
      inputData.put("refireCount", context.getRefireCount());
      inputData.put("jobRunTime", context.getJobRunTime());
      return inputData;
    } catch (Exception e) {
      log.warn("提取输入数据失败", e);
      return Map.of("error", "Failed to extract input data", "exception", e.getMessage());
    }
  }

  /**
   * 提取输出数据
   */
  private Object extractOutputData(JobExecutionContext context) {
    try {
      Map<String, Object> outputData = new HashMap<>();
      outputData.put("result", context.getResult());
      outputData.put("jobRunTime", context.getJobRunTime());
      outputData.put("refireCount", context.getRefireCount());
      outputData.put("fireTime", context.getFireTime());
      outputData.put("scheduledFireTime", context.getScheduledFireTime());
      outputData.put("previousFireTime", context.getPreviousFireTime());
      outputData.put("nextFireTime", context.getNextFireTime());
      return outputData;
    } catch (Exception e) {
      log.warn("提取输出数据失败", e);
      return Map.of("error", "Failed to extract output data", "exception", e.getMessage());
    }
  }

  /**
   * 获取异常堆栈信息
   */
  private String getStackTrace(Throwable throwable) {
    try {
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      throwable.printStackTrace(pw);
      return sw.toString();
    } catch (Exception e) {
      return "Failed to get stack trace: " + e.getMessage();
    }
  }
}
