package dev.flexmodel.scheduling.job;

import dev.flexmodel.functions.FunctionService;
import dev.flexmodel.common.trace.TraceContext;
import dev.flexmodel.common.trace.TraceContextHolder;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.util.HashMap;
import java.util.Map;

/**
 * 边缘函数执行任务
 * 用于 Quartz 定时调度执行边缘函数
 *
 * @author cjbi
 */
@Slf4j
public class ScheduledFunctionExecutionJob implements Job {

  private final FunctionService functionService;

  public ScheduledFunctionExecutionJob() {
    // CDI.current() is required here: Quartz instantiates Job instances via its own factory,
    // so this class is not CDI-managed and @Inject cannot be used.
    functionService = CDI.current().select(FunctionService.class).get();
  }

  @Override
  @ActivateRequestContext
  public void execute(JobExecutionContext context) throws JobExecutionException {
    String projectId = context.getJobDetail().getJobDataMap().getString("projectId");
    String functionName = context.getJobDetail().getJobDataMap().getString("jobId");
    String triggerId = context.getJobDetail().getJobDataMap().getString("triggerId");

    if (functionName == null) {
      log.error("边缘函数执行任务缺少必要参数: functionName=null");
      throw new JobExecutionException("边缘函数执行任务缺少必要参数");
    }

    log.info("开始执行定时边缘函数任务: triggerId={}, functionName={}", triggerId, functionName);

    // trace scope 由 ScheduledFlowExecutionJobListener.jobToBeExecuted 生成并存入 context；
    // 此处通过 ScopedValue 绑定，使函数调用（FunctionRuntimeClientHeadersFactory 注入 traceparent）在链路上下文内执行。
    TraceContext.TraceScope traceScope = (TraceContext.TraceScope) context.get(ScheduledFlowExecutionJobListener.SPAN_SCOPE_KEY);
    Map<String, Object> invokeBody = Map.of("triggerId", triggerId, "triggerTime", System.currentTimeMillis());
    Response response = traceScope != null
      ? TraceContextHolder.with(traceScope, () -> functionService.invoke(projectId, functionName, invokeBody))
      : functionService.invoke(projectId, functionName, invokeBody);

    int status = response.getStatus();
    // 读取边缘函数返回内容作为出参
    Object responseBody = response.hasEntity() ? response.readEntity(Object.class) : null;
    response.close();

    // 判断 HTTP 状态是否成功（2xx），失败则抛异常记录失败信息
    if (status < 200 || status >= 300) {
      throw new JobExecutionException("边缘函数执行失败 [" + functionName + "]: HTTP " + status
        + " - " + (responseBody != null ? responseBody : "no response body"));
    }

    Map<String, Object> result = new HashMap<>();
    result.put("success", true);
    result.put("errMsg", "");
    result.put("functionName", functionName);
    result.put("triggerId", triggerId);
    result.put("status", status);
    result.put("data", responseBody);
    context.setResult(result);
  }
}
