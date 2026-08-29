package dev.flexmodel.scheduling.job;

import dev.flexmodel.functions.FunctionService;
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
    // span 已由 ScheduledFlowExecutionJobListener 在 jobToBeExecuted 中启动并激活，
    // FunctionRuntimeClientHeadersFactory 通过 Span.current() 自动注入 traceparent
    String functionName = context.getJobDetail().getJobDataMap().getString("jobId");
    String triggerId = context.getJobDetail().getJobDataMap().getString("triggerId");

    if (functionName == null) {
      log.error("边缘函数执行任务缺少必要参数: functionName=null");
      throw new JobExecutionException("边缘函数执行任务缺少必要参数");
    }

    log.info("开始执行定时边缘函数任务: triggerId={}, functionName={}", triggerId, functionName);

    Response response = functionService.invoke(
      projectId, functionName, Map.of("triggerId", triggerId, "triggerTime", System.currentTimeMillis()));

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
